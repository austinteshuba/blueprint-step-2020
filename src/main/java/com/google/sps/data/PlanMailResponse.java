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

package com.google.sps.data;

import com.google.sps.utility.DateInterval;
import java.util.List;

/** Class containing the response to be converted to Json. */
public final class PlanMailResponse {

  private final int wordCount;
  private final int averageReadingSpeed;
  private final int minutesToRead;
  private final List<DateInterval> potentialEventTimes;

  /** Initialize the class with all the parameters required. */
  public PlanMailResponse(
      int wordCount,
      int averageReadingSpeed,
      int minutesToRead,
      List<DateInterval> potentialEventTimes) {
    this.wordCount = wordCount;
    this.averageReadingSpeed = averageReadingSpeed;
    this.minutesToRead = minutesToRead;
    this.potentialEventTimes = potentialEventTimes;
  }

  public int getWordCount() {
    return wordCount;
  }

  public int getAverageReadingSpeed() {
    return averageReadingSpeed;
  }

  public int getMinutesToRead() {
    return minutesToRead;
  }

  public List<DateInterval> getPotentialEventTimes() {
    return potentialEventTimes;
  }
}
