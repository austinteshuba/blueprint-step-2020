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

package com.google.sps.utility;

import com.google.sps.data.CalendarSummaryResponse;
import com.google.sps.data.TimeHourMin;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Class containing the user's free hours. */
public final class FreeTimeUtility {

  private final Date startDate;
  /**
   * numDays represents the number of days from the current one (and including it) we are
   * considering for our calculations It is used to initialize the free interval lists and the size
   * of the free time lists that are returned
   */
  private final int numDays;
  /**
   * The lists of time intervals from now (startDate) to the end day. They are divided by morning,
   * work, and evening periods to allow easy computation of free hours, and later to create events
   * during work/morning/evening hours
   */
  private List<DateInterval> morningFreeInterval;

  private List<DateInterval> workFreeInterval;
  private List<DateInterval> eveningFreeInterval;
  private static final int DAYS_IN_WEEK = 7;

  /**
   * Initialize the class with the start day. The work hours and personal hours are provided as
   * parameters of the constructor.
   *
   * @param startTime parameter that gives the time of the start/now.
   * @param personalBeginHour parameter that gives the hour to begin the personal time
   * @param workBeginHour parameter that gives the hour to begin the work time
   * @param workEndHour parameter that gives the hour to begin the work time
   */
  public FreeTimeUtility(
      Date startDate,
      int personalBeginHour,
      int workBeginHour,
      int workEndHour,
      int personalEndHour,
      int numDays) {
    this.startDate = startDate;
    this.numDays = numDays;
    Date basisDate = new Date(startDate.getYear(), startDate.getMonth(), startDate.getDate(), 0, 0);

    this.morningFreeInterval = new ArrayList<>();
    this.workFreeInterval = new ArrayList<>();
    this.eveningFreeInterval = new ArrayList<>();

    for (int day = 0; day < numDays; day++) {
      Date currentDate = Date.from(basisDate.toInstant().plus(Duration.ofDays(day)));
      Date workStart = Date.from(currentDate.toInstant().plus(Duration.ofHours(workBeginHour)));
      Date workEnd = Date.from(currentDate.toInstant().plus(Duration.ofHours(workEndHour)));
      Date personalStart =
          Date.from(currentDate.toInstant().plus(Duration.ofHours(personalBeginHour)));
      Date personalEnd = Date.from(currentDate.toInstant().plus(Duration.ofHours(personalEndHour)));
      morningFreeInterval.add(new DateInterval(personalStart, workStart));
      workFreeInterval.add(new DateInterval(workStart, workEnd));
      eveningFreeInterval.add(new DateInterval(workEnd, personalEnd));
    }
    addEvent(basisDate, startDate);
  }

  /**
   * Change the free intervals based on the new event added.
   *
   * @param evenStart parameter that gives start time.
   * @param eventEnd parameter that gives end tome of the event
   */
  public void addEvent(Date eventStart, Date eventEnd) {
    this.morningFreeInterval = updateInterval(this.morningFreeInterval, eventStart, eventEnd);
    this.workFreeInterval = updateInterval(this.workFreeInterval, eventStart, eventEnd);
    this.eveningFreeInterval = updateInterval(this.eveningFreeInterval, eventStart, eventEnd);
  }

  /**
   * Method to change the list of free intervals according to a new event, that may occupy parts of
   * the intervals
   *
   * @param freeTime the list of intervals to update.
   * @param eventStart the start time of the new event
   * @param eventEnd the end time of the new event
   * @return the new list of intervals
   */
  private List<DateInterval> updateInterval(
      List<DateInterval> freeTime, Date eventStart, Date eventEnd) {
    List<DateInterval> updatedTime = new ArrayList<>();
    for (DateInterval interval : freeTime) {
      // interval:      |--------------|
      // event:              |----|
      // new intervals: |----|    |----|
      if (interval.getStart().before(eventStart) && interval.getEnd().after(eventEnd)) {
        updatedTime.add(new DateInterval(interval.getStart(), eventStart));
        updatedTime.add(new DateInterval(eventEnd, interval.getEnd()));
      }
      // interval:      |--------------|
      // event:              |------------|
      // new interval:  |----|
      else if (interval.getStart().before(eventStart) && interval.getEnd().after(eventStart)) {
        updatedTime.add(new DateInterval(interval.getStart(), eventStart));
      }
      // interval:       |--------------|
      // event:        |-----------|
      // new interval:             |----|
      else if (interval.getStart().before(eventEnd) && interval.getEnd().after(eventEnd)) {
        updatedTime.add(new DateInterval(eventEnd, interval.getEnd()));
      }
      // interval:     |------|
      // event:                 |----|
      // new interval: |------|
      else if (!((eventStart.before(interval.getStart()) || eventStart.equals(interval.getStart()))
          && (eventEnd.after(interval.getEnd()) || eventEnd.equals(interval.getEnd())))) {
        updatedTime.add(interval);
      }
      // Remaining case, excluded from the updated list of intervals.
      // interval:    |----|
      // event:    |-----------|
    }
    return updatedTime;
  }

  /**
   * Method to accumulate the free time on a particular list of free times by adding the time of
   * each interval on a the given list of free time intervals. To get the index to update, find the
   * difference in Day between the interval date and the start date. In case the difference is
   * negative (for example the start day is friday and we are monday), adjust the index by adding 7
   *
   * @param hoursPerDay the list of free time in milliseconds to update.
   * @param freeInterval the list of free intervals
   * @return the updated list of free time
   */
  private List<Long> updateHoursPerDay(List<Long> hoursPerDay, List<DateInterval> freeInterval) {
    for (DateInterval interval : freeInterval) {
      // Find the index to update as the difference in day between the interval day and the start
      // day
      // Adjust the index if it is negative (for example the start day is friday (5) and we are
      // monday (1))
      // by adding 7.
      int index = interval.getStart().getDay() - this.startDate.getDay();
      index = index < 0 ? index + DAYS_IN_WEEK : index;
      hoursPerDay.set(
          index,
          hoursPerDay.get(index) + interval.getEnd().getTime() - interval.getStart().getTime());
    }
    return hoursPerDay;
  }

  private List<TimeHourMin> convertTime(List<Long> hoursPerDay) {
    List<TimeHourMin> freeTime = new ArrayList<>();
    for (long durationMilli : hoursPerDay) {
      int hours = (int) (durationMilli / TimeUnit.HOURS.toMillis(1));
      int minutes =
          (int) ((durationMilli % TimeUnit.HOURS.toMillis(1)) / TimeUnit.MINUTES.toMillis(1));
      freeTime.add(new TimeHourMin(hours, minutes));
    }
    return freeTime;
  }

  /**
   * Method to create and return the calendar data response.
   *
   * @return the calendar data response to be sent as servlet response
   */
  public CalendarSummaryResponse getCalendarSummaryResponse() {
    long zero = 0;
    List<Long> workHoursPerDay = new ArrayList<Long>(Collections.nCopies(this.numDays, (long) 0));
    List<Long> personalHoursPerDay =
        new ArrayList<Long>(Collections.nCopies(this.numDays, (long) 0));
    workHoursPerDay = updateHoursPerDay(workHoursPerDay, this.workFreeInterval);
    personalHoursPerDay = updateHoursPerDay(personalHoursPerDay, this.morningFreeInterval);
    personalHoursPerDay = updateHoursPerDay(personalHoursPerDay, this.eveningFreeInterval);
    List<TimeHourMin> workTimeFree = convertTime(workHoursPerDay);
    List<TimeHourMin> personalTimeFree = convertTime(personalHoursPerDay);
    return new CalendarSummaryResponse(this.startDate.getDay(), workTimeFree, personalTimeFree);
  }

  public List<DateInterval> getWorkFreeInterval() {
    return this.workFreeInterval;
  }
}
