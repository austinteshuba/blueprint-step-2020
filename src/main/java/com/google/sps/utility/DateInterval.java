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

import com.google.sps.data.PlanMailResponse;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.*;

/** Class implementing an Interval Data Structure. */
public final class DateInterval {

  private final Date start;
  private final Date end;

  /**
   * Constructor for the class
   *
   * @param start the start time of the interval
   * @param end the end time of the interval
   */
  public DateInterval(Date start, Date end) {
    this.start = start;
    this.end = end;
  }

  public Date getStart() {
    return this.start;
  }

  public Date getEnd() {
    return this.end;
  }

  @Override
  public int hashCode() {
    return new HashCodeBuilder()
            .append(start)
            .append(end)
            .toHashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DateInterval)) {
      return false;
    }
    if (o == this) {
      return true;
    }

    DateInterval dateInterval = (DateInterval) o;

    return new EqualsBuilder()
            .append(start, dateInterval.getStart())
            .append(end, dateInterval.getEnd())
            .isEquals();
  }
}
