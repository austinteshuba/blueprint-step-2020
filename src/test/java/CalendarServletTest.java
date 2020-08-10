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
import com.google.appengine.repackaged.com.google.gson.Gson;
import com.google.common.collect.ImmutableList;
import com.google.sps.data.CalendarSummaryResponse;
import com.google.sps.model.CalendarClient;
import com.google.sps.model.CalendarClientFactory;
import com.google.sps.servlets.CalendarServlet;
import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
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
public final class CalendarServletTest extends AuthenticatedServletTestBase {
  private CalendarClientFactory calendarClientFactory;
  private CalendarClient calendarClient;
  private CalendarServlet servlet;

  private static final Gson gson = new Gson();

  private static final String EVENT_SUMMARY_ONE = "test event one";
  private static final String EVENT_SUMMARY_TWO = "test event two";
  private static final int OFFSET_YEAR = 1900;
  private static final String EVENT_UNDEFINED_JSON = "[{}]";
  private static final String EMPTY_JSON = "[]";
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
  private static final EventDateTime START_ONE =
      new EventDateTime().setDateTime(new DateTime(EVENT_ONE_START));
  private static final EventDateTime END_ONE =
      new EventDateTime().setDateTime(new DateTime(EVENT_ONE_END));
  private static final EventDateTime START_TWO =
      new EventDateTime().setDateTime(new DateTime(EVENT_TWO_START));
  private static final EventDateTime END_TWO =
      new EventDateTime().setDateTime(new DateTime(EVENT_TWO_END));
  private static final List<Event> NO_EVENT = ImmutableList.of();
  private static final List<Event> EVENT_ONE =
      ImmutableList.of(
          new Event().setSummary(EVENT_SUMMARY_ONE).setStart(START_ONE).setEnd(END_ONE));
  private static final List<Event> EVENT_TWO =
      ImmutableList.of(
          new Event().setSummary(EVENT_SUMMARY_TWO).setStart(START_TWO).setEnd(END_TWO));
  private static final long hour = TimeUnit.HOURS.toMillis(1);

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    calendarClientFactory = Mockito.mock(CalendarClientFactory.class);
    calendarClient = Mockito.mock(CalendarClient.class);
    servlet = new CalendarServlet(authenticationVerifier, calendarClientFactory);

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
  public void noCalendarEvent() throws Exception {
    // Test case where there are no events in the user's calendar
    // The result should be that all hours are free
    Mockito.when(calendarClient.getCalendarList()).thenReturn(ONE_CALENDAR);
    Mockito.when(calendarClient.getUpcomingEvents(PRIMARY, CURRENT_TIME, END_TIME))
        .thenReturn(NO_EVENT);
    Mockito.when(calendarClient.getCurrentTime()).thenReturn(CURRENT_TIME);
    CalendarSummaryResponse actual = getServletResponse();
    List<Integer> workHoursPerDay = new ArrayList<Integer>(Collections.nCopies(5, 8));
    List<Integer> personalHoursPerDay = Arrays.asList(6, 8, 8, 8, 8);
    Assert.assertEquals(2, actual.getStartDay());
    Assert.assertEquals(5, actual.getWorkTimeFree().size());
    Assert.assertEquals(5, actual.getPersonalTimeFree().size());
    for (int index = 0; index < workHoursPerDay.size(); index++) {
      Assert.assertEquals(
          (int) workHoursPerDay.get(index), actual.getWorkTimeFree().get(index).getHours());
      Assert.assertEquals(0, actual.getWorkTimeFree().get(index).getMinutes());
      Assert.assertEquals(
          (int) personalHoursPerDay.get(index), actual.getPersonalTimeFree().get(index).getHours());
      Assert.assertEquals(0, actual.getPersonalTimeFree().get(index).getMinutes());
    }
  }

  @Test
  public void twoCalendars() throws Exception {
    // Test case where there are two calendars with a defined event in each
    // Event with 1 working hour and event overlapping 1 hour with personal time.
    Mockito.when(calendarClient.getCalendarList()).thenReturn(TWO_CALENDARS);
    Mockito.when(calendarClient.getUpcomingEvents(PRIMARY, CURRENT_TIME, END_TIME))
        .thenReturn(EVENT_ONE);
    Mockito.when(calendarClient.getUpcomingEvents(SECONDARY, CURRENT_TIME, END_TIME))
        .thenReturn(EVENT_TWO);
    Mockito.when(calendarClient.getCurrentTime()).thenReturn(CURRENT_TIME);
    CalendarSummaryResponse actual = getServletResponse();
    List<Integer> workHoursPerDay = Arrays.asList(7, 8, 8, 8, 8);
    List<Integer> personalHoursPerDay = Arrays.asList(6, 7, 8, 8, 8);
    Assert.assertEquals(2, actual.getStartDay());
    Assert.assertEquals(5, actual.getWorkTimeFree().size());
    Assert.assertEquals(5, actual.getPersonalTimeFree().size());
    for (int index = 0; index < workHoursPerDay.size(); index++) {
      Assert.assertEquals(
          (int) workHoursPerDay.get(index), actual.getWorkTimeFree().get(index).getHours());
      Assert.assertEquals(0, actual.getWorkTimeFree().get(index).getMinutes());
      Assert.assertEquals(
          (int) personalHoursPerDay.get(index), actual.getPersonalTimeFree().get(index).getHours());
      Assert.assertEquals(0, actual.getPersonalTimeFree().get(index).getMinutes());
    }
  }

  private CalendarSummaryResponse getServletResponse() throws IOException, ServletException {
    // Method that handles the request once the Calendar Client has been mocked
    servlet.doGet(request, response);
    String actualString = stringWriter.toString();
    CalendarSummaryResponse actual = gson.fromJson(actualString, CalendarSummaryResponse.class);
    return actual;
  }
}
