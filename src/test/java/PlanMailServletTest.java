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

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.gmail.model.Message;
import com.google.appengine.repackaged.com.google.gson.Gson;
import com.google.common.collect.ImmutableList;
import com.google.sps.data.PlanMailResponse;
import com.google.sps.model.CalendarClient;
import com.google.sps.model.CalendarClientFactory;
import com.google.sps.model.GmailClient;
import com.google.sps.model.GmailClientFactory;
import com.google.sps.model.PlanMailResponseHelper;
import com.google.sps.servlets.PlanMailServlet;
import com.google.sps.utility.DateInterval;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.AdditionalAnswers;
import org.mockito.Mockito;

/** Test Calendar Servlet responds to client with correctly parsed Events. */
@RunWith(JUnit4.class)
public final class PlanMailServletTest extends AuthenticatedServletTestBase {
  private CalendarClientFactory calendarClientFactory;
  private CalendarClient calendarClient;
  private GmailClient gmailClient;
  private PlanMailResponseHelper planMailResponseHelper;
  private PlanMailServlet servlet;

  private static final Gson gson = new Gson();

  private static final String EVENT_SUMMARY_ONE = "test event one";
  private static final String EVENT_SUMMARY_TO_READ = "Read emails";
  private static final String EVENT_SUMMARY_THREE = "test event three";
  private static final int OFFSET_YEAR = 1900;
  private static final int NUMBER_DAYS_UNREAD = 7;
  private static final int TEST_WORD_COUNT = 500;
  private static final GmailClient.MessageFormat MESSAGE_FORMAT = GmailClient.MessageFormat.FULL;
  private static final CalendarListEntry PRIMARY = new CalendarListEntry().setId("primary");
  private static final CalendarListEntry SECONDARY = new CalendarListEntry().setId("secondary");
  private static final List<CalendarListEntry> ONE_CALENDAR = ImmutableList.of(PRIMARY);
  private static final List<CalendarListEntry> TWO_CALENDARS = ImmutableList.of(PRIMARY, SECONDARY);
  private static final Date CURRENT_TIME = new Date(2020 - OFFSET_YEAR, 4, 19, 9, 0);
  private static final Date END_TIME = Date.from(CURRENT_TIME.toInstant().plus(Duration.ofDays(5)));
  private static final Date EVENT_ONE_START = new Date(2020 - OFFSET_YEAR, 4, 19, 15, 0);
  private static final Date EVENT_ONE_END = new Date(2020 - OFFSET_YEAR, 4, 19, 16, 0);
  private static final Date EVENT_TWO_START = new Date(2020 - OFFSET_YEAR, 4, 20, 6, 0);
  private static final Date EVENT_TWO_END = new Date(2020 - OFFSET_YEAR, 4, 20, 8, 0);
  private static final Date EVENT_THREE_START = new Date(2020 - OFFSET_YEAR, 4, 19, 10, 5);
  private static final Date EVENT_THREE_END = new Date(2020 - OFFSET_YEAR, 4, 19, 10, 30);
  private static final EventDateTime START_ONE =
      new EventDateTime().setDateTime(new DateTime(EVENT_ONE_START));
  private static final EventDateTime END_ONE =
      new EventDateTime().setDateTime(new DateTime(EVENT_ONE_END));
  private static final EventDateTime START_TWO =
      new EventDateTime().setDateTime(new DateTime(EVENT_TWO_START));
  private static final EventDateTime END_TWO =
      new EventDateTime().setDateTime(new DateTime(EVENT_TWO_END));
  private static final EventDateTime START_THREE =
      new EventDateTime().setDateTime(new DateTime(EVENT_THREE_START));
  private static final EventDateTime END_THREE =
      new EventDateTime().setDateTime(new DateTime(EVENT_THREE_END));
  private static final List<Event> NO_EVENT = ImmutableList.of();
  private static final List<Event> EVENT_ONE =
      ImmutableList.of(
          new Event().setSummary(EVENT_SUMMARY_ONE).setStart(START_ONE).setEnd(END_ONE));
  private static final List<Event> EVENT_TWO =
      ImmutableList.of(
          new Event().setSummary(EVENT_SUMMARY_TO_READ).setStart(START_TWO).setEnd(END_TWO));
  private static final List<Event> EVENT_THREE =
      ImmutableList.of(
          new Event().setSummary(EVENT_SUMMARY_THREE).setStart(START_THREE).setEnd(END_THREE));
  private static final List<Message> MESSAGES = ImmutableList.of();

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    calendarClientFactory = Mockito.mock(CalendarClientFactory.class);
    calendarClient = Mockito.mock(CalendarClient.class);
    GmailClientFactory gmailClientFactory = Mockito.mock(GmailClientFactory.class);
    gmailClient = Mockito.mock(GmailClient.class);
    planMailResponseHelper = Mockito.mock(PlanMailResponseHelper.class);
    servlet =
        new PlanMailServlet(
            authenticationVerifier,
            calendarClientFactory,
            gmailClientFactory,
            planMailResponseHelper);
    Mockito.when(gmailClientFactory.getGmailClient(Mockito.any())).thenReturn(gmailClient);
    Mockito.when(calendarClientFactory.getCalendarClient(Mockito.any())).thenReturn(calendarClient);
    // Writer used in get/post requests to capture HTTP response values
    stringWriter = new StringWriter();

    request = Mockito.mock(HttpServletRequest.class);
    response =
        Mockito.mock(
            HttpServletResponse.class,
            AdditionalAnswers.delegatesTo(new HttpServletResponseFake(stringWriter)));
    Mockito.when(request.getCookies()).thenReturn(validCookies);
  }

  @Test
  public void noPotentialEvent() throws Exception {
    // Test case where there are no unread emails
    // We should get no proposed potential event times
    Mockito.when(calendarClient.getCalendarList()).thenReturn(ONE_CALENDAR);
    Mockito.when(calendarClient.getUpcomingEvents(PRIMARY, CURRENT_TIME, END_TIME))
        .thenReturn(NO_EVENT);
    Mockito.when(calendarClient.getCurrentTime()).thenReturn(CURRENT_TIME);
    Mockito.when(gmailClient.getUnreadEmailsFromNDays(MESSAGE_FORMAT, NUMBER_DAYS_UNREAD))
        .thenReturn(MESSAGES);
    Mockito.when(planMailResponseHelper.getWordCount(Mockito.any())).thenReturn(0);
    PlanMailResponse actual = getServletResponse();
    PlanMailResponse expected = new PlanMailResponse(0, 50, 0, new ArrayList<>());
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void onePotentialEvent() throws Exception {
    // Test case where there are unread emails that will take 10 minutes to read
    // We should get 1 proposed event that takes 10 minutes
    Mockito.when(calendarClient.getCalendarList()).thenReturn(ONE_CALENDAR);
    Mockito.when(calendarClient.getUpcomingEvents(PRIMARY, CURRENT_TIME, END_TIME))
        .thenReturn(EVENT_ONE);
    Mockito.when(calendarClient.getCurrentTime()).thenReturn(CURRENT_TIME);
    Mockito.when(gmailClient.getUnreadEmailsFromNDays(MESSAGE_FORMAT, NUMBER_DAYS_UNREAD))
        .thenReturn(MESSAGES);
    Mockito.when(planMailResponseHelper.getWordCount(Mockito.any())).thenReturn(TEST_WORD_COUNT);
    PlanMailResponse actual = getServletResponse();
    List<Date> startHour = Arrays.asList(new Date(2020 - OFFSET_YEAR, 4, 19, 10, 0));
    List<Date> endHour = Arrays.asList(new Date(2020 - OFFSET_YEAR, 4, 19, 10, 10));
    List<DateInterval> meetingTimes = new ArrayList<>();
    for (int index = 0; index < startHour.size(); index++) {
      meetingTimes.add(new DateInterval(startHour.get(index), endHour.get(index)));
    }
    PlanMailResponse expected = new PlanMailResponse(500, 50, 10, meetingTimes);
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void alreadyCreatedEvent() throws Exception {
    // Test case where there are unread emails that will take 10 minutes to read
    // But an event (event 2) has already been created to go through them
    // We should get no proposed potential event
    Mockito.when(calendarClient.getCalendarList()).thenReturn(TWO_CALENDARS);
    Mockito.when(calendarClient.getUpcomingEvents(PRIMARY, CURRENT_TIME, END_TIME))
        .thenReturn(EVENT_ONE);
    Mockito.when(calendarClient.getUpcomingEvents(SECONDARY, CURRENT_TIME, END_TIME))
        .thenReturn(EVENT_TWO);
    Mockito.when(calendarClient.getCurrentTime()).thenReturn(CURRENT_TIME);
    Mockito.when(gmailClient.getUnreadEmailsFromNDays(MESSAGE_FORMAT, NUMBER_DAYS_UNREAD))
        .thenReturn(MESSAGES);
    Mockito.when(planMailResponseHelper.getWordCount(Mockito.any())).thenReturn(TEST_WORD_COUNT);
    PlanMailResponse actual = getServletResponse();
    PlanMailResponse expected = new PlanMailResponse(500, 50, 10, new ArrayList<>());
    Assert.assertEquals(500, actual.getWordCount());
    Assert.assertEquals(50, actual.getAverageReadingSpeed());
    Assert.assertEquals(10, actual.getMinutesToRead());
    Assert.assertEquals(expected, actual);
  }

  @Test
  public void splitPotentialEvent() throws Exception {
    // Test case where there are unread emails that will take 10 minutes to read
    // and an event that splits the time interval required in two parts
    // We should get 2 proposed events of 10 minutes each.
    Mockito.when(calendarClient.getCalendarList()).thenReturn(ONE_CALENDAR);
    Mockito.when(calendarClient.getUpcomingEvents(PRIMARY, CURRENT_TIME, END_TIME))
        .thenReturn(EVENT_THREE);
    Mockito.when(calendarClient.getCurrentTime()).thenReturn(CURRENT_TIME);
    Mockito.when(gmailClient.getUnreadEmailsFromNDays(MESSAGE_FORMAT, NUMBER_DAYS_UNREAD))
        .thenReturn(MESSAGES);
    Mockito.when(planMailResponseHelper.getWordCount(Mockito.any())).thenReturn(TEST_WORD_COUNT);
    PlanMailResponse actual = getServletResponse();
    List<Date> startHour =
        Arrays.asList(
            new Date(2020 - OFFSET_YEAR, 4, 19, 10, 0),
            new Date(2020 - OFFSET_YEAR, 4, 19, 10, 30));
    List<Date> endHour =
        Arrays.asList(
            new Date(2020 - OFFSET_YEAR, 4, 19, 10, 5),
            new Date(2020 - OFFSET_YEAR, 4, 19, 10, 35));
    Assert.assertEquals(500, actual.getWordCount());
    List<DateInterval> meetingTimes = new ArrayList<>();
    for (int index = 0; index < startHour.size(); index++) {
      meetingTimes.add(new DateInterval(startHour.get(index), endHour.get(index)));
    }
    PlanMailResponse expected = new PlanMailResponse(500, 50, 10, meetingTimes);
    Assert.assertEquals(expected, actual);
  }

  private PlanMailResponse getServletResponse() throws IOException, ServletException {
    // Method that handles the request once the Calendar Client has been mocked
    servlet.doGet(request, response);
    String actualString = stringWriter.toString();
    PlanMailResponse actual = gson.fromJson(actualString, PlanMailResponse.class);
    return actual;
  }
}
