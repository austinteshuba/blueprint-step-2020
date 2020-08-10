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

import com.google.api.services.calendar.model.CalendarListEntry;
import com.google.api.services.calendar.model.Event;
import java.io.IOException;
import java.util.Date;
import java.util.List;

/** Interface to handle get requests to the Calendar API. */
public interface CalendarClient {
  /**
   * Get the list of calendars in a user's account.
   *
   * @return the list of Calendars from the user's account
   * @throws IOException thrown when an issue occurs
   */
  List<CalendarListEntry> getCalendarList() throws IOException;

  /**
   * Get the events in a user's calendar.
   *
   * @return the list of Event from a calendar
   * @throws IOException thrown when an issue occurs
   */
  List<Event> getCalendarEvents(CalendarListEntry calendarList) throws IOException;

  /**
   * Get the events in the specified time boundary. The API returns evens if they are fully or
   * partially within the boundaries.
   *
   * @param calendarList the calendar to get events from
   * @param timeMin the minimum time to get the events.
   * @param timeMax the maximum time to get the events.
   * @return the list of Event from a calendar.
   * @throws IOException thrown when an issue occurs.
   */
  List<Event> getUpcomingEvents(CalendarListEntry calendarList, Date timeMin, Date timeMax)
      throws IOException;

  /**
   * Get the current time from the system.
   *
   * @return the Date object created
   * @throws IOException thrown when an issue occurs
   */
  Date getCurrentTime() throws IOException;

  /**
   * Create an event on the calendar with the given calendarId. Use the given start and end periods
   * and the summary provided in the parameters
   *
   * @param start the start time of the event
   * @param end the end time of the event
   * @param summary the summary/title of the event
   * @param calendarId the Id of the calendar where to create the event
   * @throws IOException thrown when an issue occurs.
   */
  void createNewEvent(Date start, Date end, String summary, String calendarId) throws IOException;
}
