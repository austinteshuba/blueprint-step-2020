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
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.sps.model.AuthenticatedHttpServlet;
import com.google.sps.model.AuthenticationVerifier;
import com.google.sps.model.CalendarClient;
import com.google.sps.model.CalendarClientFactory;
import com.google.sps.model.CalendarClientImpl;
import com.google.sps.utility.FreeTimeUtility;
import com.google.sps.utility.JsonUtility;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** GET function responds JSON string containing events in user's calendar. */
@WebServlet("/calendar")
public class CalendarServlet extends AuthenticatedHttpServlet {
  private final CalendarClientFactory calendarClientFactory;
  private static final int PERSONAL_BEGIN_HOUR = 7;
  private static final int WORK_BEGIN_HOUR = 10;
  private static final int WORK_END_HOUR = 18;
  private static final int PERSONAL_END_HOUR = 23;
  private static final int NUM_DAYS = 5;

  /** Create servlet with default CalendarClient and Authentication Verifier implementations */
  public CalendarServlet() {
    calendarClientFactory = new CalendarClientImpl.Factory();
  }

  /**
   * Create servlet with explicit implementations of CalendarClient and AuthenticationVerifier
   *
   * @param authenticationVerifier implementation of AuthenticationVerifier
   * @param calendarClientFactory implementation of CalendarClientFactory
   */
  public CalendarServlet(
      AuthenticationVerifier authenticationVerifier, CalendarClientFactory calendarClientFactory) {
    super(authenticationVerifier);
    this.calendarClientFactory = calendarClientFactory;
  }

  /**
   * Returns CalendarData string containing the user's free hours, both personal and work hours
   *
   * @param request Http request from the client. Should contain idToken and accessToken
   * @param response 403 if user is not authenticated, or Json string with the user's events
   * @throws IOException if an issue arises while processing the request
   */
  @Override
  public void doGet(
      HttpServletRequest request, HttpServletResponse response, Credential googleCredential)
      throws IOException {
    assert googleCredential != null
        : "Null credentials (i.e. unauthenticated requests) should already be handled";

    CalendarClient calendarClient = calendarClientFactory.getCalendarClient(googleCredential);
    long fiveDaysInMillis = TimeUnit.DAYS.toMillis(NUM_DAYS);
    Date timeMin = calendarClient.getCurrentTime();
    Date timeMax = Date.from(timeMin.toInstant().plus(Duration.ofDays(NUM_DAYS)));
    List<Event> calendarEvents = getEvents(calendarClient, timeMin, timeMax);

    FreeTimeUtility freeTimeUtility =
        new FreeTimeUtility(
            timeMin,
            PERSONAL_BEGIN_HOUR,
            WORK_BEGIN_HOUR,
            WORK_END_HOUR,
            PERSONAL_END_HOUR,
            NUM_DAYS);
    for (Event event : calendarEvents) {
      DateTime start = event.getStart().getDateTime();
      start = start == null ? event.getStart().getDate() : start;
      DateTime end = event.getEnd().getDateTime();
      end = end == null ? event.getEnd().getDate() : end;
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

    // Convert event list to JSON and print to response
    JsonUtility.sendJson(response, freeTimeUtility.getCalendarSummaryResponse());
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
   * Creates a new event when POST method called. Use the time boundaries given in the request for
   * creating the event in the primary calendar. The summary for this event is Read emails.
   *
   * @param request Http request from the client. Body contains start and end parameters
   * @param response String saying the event has been created
   * @throws IOException if an issue arises while processing the request
   */
  @Override
  public void doPost(
      HttpServletRequest request, HttpServletResponse response, Credential googleCredential)
      throws IOException {
    assert googleCredential != null
        : "Null credentials (i.e. unauthenticated requests) should already be handled";

    CalendarClient calendarClient = calendarClientFactory.getCalendarClient(googleCredential);
    Date start = new Date(request.getParameter("start"));
    Date end = new Date(request.getParameter("end"));
    String summary = request.getParameter("summary");
    String calendarId = request.getParameter("id");
    calendarClient.createNewEvent(start, end, summary, calendarId);

    JsonUtility.sendJson(response, "Event created");
  }
}
