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

package com.google.sps.model;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.appengine.http.UrlFetchTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.sps.utility.ServletUtility;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Handles basic GET/POST requests to and from the Google Calendar service */
public class CalendarClientImpl implements CalendarClient {

  private final Calendar calendarService;

  private CalendarClientImpl(Credential credential) {
    HttpTransport transport = UrlFetchTransport.getDefaultInstance();
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    String applicationName = ServletUtility.APPLICATION_NAME;

    calendarService =
        new Calendar.Builder(transport, jsonFactory, credential)
            .setApplicationName(applicationName)
            .build();
  }

  @Override
  public List<CalendarListEntry> getCalendarList() throws IOException {
    // returns null if no calendar exists. Convert to empty list for ease.
    List<CalendarListEntry> calendarList =
        calendarService.calendarList().list().execute().getItems();

    return calendarList != null ? calendarList : new ArrayList<>();
  }

  @Override
  public List<Event> getCalendarEvents(CalendarListEntry calendarList) throws IOException {
    // returns null if no events exist. Convert to empty list for ease.
    List<Event> events = calendarService.events().list(calendarList.getId()).execute().getItems();

    return events != null ? events : new ArrayList<>();
  }

  @Override
  public List<Event> getUpcomingEvents(CalendarListEntry calendarList, Date timeMin, Date timeMax)
      throws IOException {
    // returns null if there are no upcoming events in the next week. Convert to empty list for
    // ease.
    List<Event> events =
        calendarService
            .events()
            .list(calendarList.getId())
            .setTimeMin(new DateTime(timeMin))
            .setTimeMax(new DateTime(timeMax))
            .execute()
            .getItems();

    return events != null ? events : new ArrayList<>();
  }

  @Override
  public Date getCurrentTime() throws IOException {
    return new Date();
  }

  @Override
  public void createNewEvent(Date start, Date end, String summary, String calendarId)
      throws IOException {
    Event event = new Event();
    EventDateTime startTime = new EventDateTime().setDateTime(new DateTime(start));
    EventDateTime endTime = new EventDateTime().setDateTime(new DateTime(end));
    event.setStart(startTime);
    event.setEnd(endTime);
    event.setSummary(summary);
    calendarService.events().insert(calendarId, event).execute();
  }

  /** Factory to create a CalendarClientImpl instance with given credential */
  public static class Factory implements CalendarClientFactory {
    /**
     * Create a CalendarClientImpl instance
     *
     * @param credential valid Google credential object
     * @return CalendarClientImpl instance with credential
     */
    @Override
    public CalendarClient getCalendarClient(Credential credential) {
      return new CalendarClientImpl(credential);
    }
  }
}
