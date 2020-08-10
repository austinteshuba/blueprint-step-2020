// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.sps.servlets;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.StringUtils;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.common.base.Throwables;
import com.google.common.io.BaseEncoding;
import com.google.sps.data.PlanMailResponse;
import com.google.sps.model.AuthenticatedHttpServlet;
import com.google.sps.model.AuthenticationVerifier;
import com.google.sps.model.CalendarClient;
import com.google.sps.model.CalendarClientFactory;
import com.google.sps.model.CalendarClientImpl;
import com.google.sps.model.GmailClient;
import com.google.sps.model.GmailClientFactory;
import com.google.sps.model.GmailClientImpl;
import com.google.sps.utility.DateInterval;
import com.google.sps.utility.FreeTimeUtility;
import com.google.sps.utility.JsonUtility;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.mail.MessagingException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * GET function responds JSON string containing potential events to read mail. The servlet proposes
 * all possible event times until it is not possible. For now, the user does not get an indication
 * if the events proposed are sufficient to go through the emails.
 */
@WebServlet("/plan-mail")
public class PlanMailServlet extends AuthenticatedHttpServlet {
  private final CalendarClientFactory calendarClientFactory;
  private final GmailClientFactory gmailClientFactory;
  private static final int AVERAGE_READING_SPEED = 50;
  private static final int PERSONAL_BEGIN_HOUR = 7;
  private static final int WORK_BEGIN_HOUR = 10;
  private static final int WORK_END_HOUR = 18;
  private static final int PERSONAL_END_HOUR = 23;
  private static final int NUM_DAYS = 5;

  /** Create servlet with default CalendarClient and Authentication Verifier implementations */
  public PlanMailServlet() {
    calendarClientFactory = new CalendarClientImpl.Factory();
    gmailClientFactory = new GmailClientImpl.Factory();
  }

  /**
   * Create servlet with explicit implementations of CalendarClient and AuthenticationVerifier
   *
   * @param authenticationVerifier implementation of AuthenticationVerifier
   * @param calendarClientFactory implementation of CalendarClientFactory
   */
  public PlanMailServlet(
      AuthenticationVerifier authenticationVerifier,
      CalendarClientFactory calendarClientFactory,
      GmailClientFactory gmailClientFactory) {
    super(authenticationVerifier);
    this.calendarClientFactory = calendarClientFactory;
    this.gmailClientFactory = gmailClientFactory;
  }

  /**
   * Returns string containing the some of the user's free time intervals, where events can be
   * created
   *
   * @param request Http request from the client. Should contain idToken and accessToken
   * @param response Json string with the user's events
   * @throws IOException if an issue arises while processing the request
   */
  @Override
  public void doGet(
      HttpServletRequest request, HttpServletResponse response, Credential googleCredential)
      throws IOException {
    assert googleCredential != null
        : "Null credentials (i.e. unauthenticated requests) should already be handled";

    CalendarClient calendarClient = calendarClientFactory.getCalendarClient(googleCredential);
    long fiveDaysInMillis = TimeUnit.DAYS.toMillis(5);
    Date timeMin = calendarClient.getCurrentTime();
    Date timeMax = Date.from(timeMin.toInstant().plus(Duration.ofDays(NUM_DAYS)));
    List<Event> calendarEvents = getEvents(calendarClient, timeMin, timeMax);
    // Initialize the freeTime utility. Keep track of the free time in the next 5 days, with
    // work hours as defined between 10am and 6 pm. The rest of the time between 7 am and 11 pm
    // should be considered personal time.

    FreeTimeUtility freeTimeUtility =
        new FreeTimeUtility(
            timeMin,
            PERSONAL_BEGIN_HOUR,
            WORK_BEGIN_HOUR,
            WORK_END_HOUR,
            PERSONAL_END_HOUR,
            NUM_DAYS);
    long preAssignedTime = 0;
    // The summary for the events we are creating is the same as the defined eventSummary
    // For now this is the check we are using. We assume that the user will not create
    // events with the same summary if they are not related to reading emails.
    String eventSummary = request.getParameter("summary");
    for (Event event : calendarEvents) {
      DateTime start = event.getStart().getDateTime();
      start = start == null ? event.getStart().getDate() : start;
      DateTime end = event.getEnd().getDateTime();
      end = end == null ? event.getEnd().getDate() : end;
      if (event.getSummary().equals(eventSummary)) {
        preAssignedTime += end.getValue() - start.getValue();
      }
      Date eventStart = new Date(start.getValue());
      Date eventEnd = new Date(end.getValue());

      if (eventStart.before(timeMin)) {
        eventStart = timeMin;
      }
      if (eventEnd.after(timeMax)) {
        eventEnd = timeMax;
      }
      freeTimeUtility.addEvent(eventStart, eventEnd);
    }

    int wordCount = getWordCount(googleCredential);
    int minutesToRead = (int) Math.ceil((double) wordCount / AVERAGE_READING_SPEED);
    long timeNeeded = minutesToRead * TimeUnit.MINUTES.toMillis(1);
    timeNeeded = Math.max(0, timeNeeded - preAssignedTime);
    List<DateInterval> potentialTimes;
    if (timeNeeded > 0) {
      potentialTimes = getPotentialTimes(freeTimeUtility, timeNeeded);
    } else {
      potentialTimes = new ArrayList<>();
    }

    PlanMailResponse planMailResponse =
        new PlanMailResponse(wordCount, AVERAGE_READING_SPEED, minutesToRead, potentialTimes);
    // Convert event list to JSON and print to response
    JsonUtility.sendJson(response, planMailResponse);
  }

  /**
   * Get the list of date intervals necessary for the time needed If there is not enough time,
   * return the maximum possible date intervals
   *
   * @param freeTimeUtility the utility to get all free date intervals
   * @param timeNeeded the unix time of the time length needed
   * @return The list of date intervals necessary
   */
  private List<DateInterval> getPotentialTimes(FreeTimeUtility freeTimeUtility, long timeNeeded) {
    List<DateInterval> workFreeInterval = freeTimeUtility.getWorkFreeInterval();
    List<DateInterval> potentialEventTimes = new ArrayList<>();
    long remainingTime = timeNeeded;
    for (DateInterval interval : workFreeInterval) {
      Date potentialEnd = new Date(interval.getStart().getTime() + remainingTime);
      if (interval.getEnd().before(potentialEnd)) {
        remainingTime -= (interval.getEnd().getTime() - interval.getStart().getTime());
        potentialEventTimes.add(interval);
      } else {
        potentialEventTimes.add(new DateInterval(interval.getStart(), potentialEnd));
        break;
      }
    }
    return potentialEventTimes;
  }

  /**
   * Get the unread emails from the last week, and perform a word count for the body of each message
   *
   * @param googleCredential the credential to creare a gmailClient with
   * @return The final word count
   * @throws IOException if an issue occurs in the method
   * @throws MessagingException if an issue occurs in the method
   */
  private int getWordCount(Credential googleCredential) {
    GmailClient gmailClient = gmailClientFactory.getGmailClient(googleCredential);
    GmailClient.MessageFormat messageFormat = GmailClient.MessageFormat.FULL;
    int numberDays = 7;
    int wordCount = 0;
    List<Message> unreadMessages = new ArrayList<>();
    try {
      unreadMessages = gmailClient.getUnreadEmailsFromNDays(messageFormat, numberDays);
    } catch (IOException e) {
      throw Throwables.propagate(e);
    }
    for (Message message : unreadMessages) {
      try {
        wordCount += getMessageSize(message);
      } catch (MessagingException | IOException e) {
        throw Throwables.propagate(e);
      }
    }
    return wordCount;
  }

  /**
   * Get the events in the user's calendars
   *
   * @param calendarClient either a mock CalendarClient or a calendarClient with a valid credential
   * @param timeMin the minimum time to start looking for events
   * @param timeMax the maximum time to look for events
   * @return List of Events from all of the user's calendars
   * @throws IOException if an issue occurs in the method
   */
  private List<Event> getEvents(CalendarClient calendarClient, Date timeMin, Date timeMax)
      throws IOException {
    List<CalendarListEntry> calendarList = calendarClient.getCalendarList();
    List<Event> events = new ArrayList<>();
    for (CalendarListEntry calendar : calendarList) {
      events.addAll(calendarClient.getUpcomingEvents(calendar, timeMin, timeMax));
    }
    return events;
  }

  /**
   * Get the word-count in an individual message.
   *
   * @param message the message given for which to find a word count
   * @throws IOException if an issue occurs in the method
   * @throws MessagingException if an issue occurs in the method
   */
  private int getMessageSize(Message message) throws MessagingException, IOException {
    List<MessagePart> messageParts = message.getPayload().getParts();
    List<MessagePart> messageBody =
        messageParts.stream()
            .filter((messagePart) -> messagePart.getMimeType().equals("text/plain"))
            .collect(Collectors.toList());
    int size = 0;
    if (messageBody.isEmpty()) {
      return size;
    }
    for (MessagePart part : messageBody) {
      byte[] messageBytes = BaseEncoding.base64Url().decode(part.getBody().getData());
      String messageString = StringUtils.newStringUtf8(messageBytes);
      StringTokenizer tokens = new StringTokenizer(messageString);
      size += tokens.countTokens();
    }
    return size;
  }
}
