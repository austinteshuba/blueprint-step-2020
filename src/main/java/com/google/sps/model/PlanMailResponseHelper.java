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

import com.google.api.services.gmail.model.Message;
import java.util.List;

/** Contains business logic to compute the word count in unread emails for the PlanMailServlet */
public interface PlanMailResponseHelper {
  /**
   * Gets the word count in the unread emails from the past days. The period length to go through
   * the emails is specified in the parameter passed in the method
   *
   * @param numberDaysUnread the number of days to look through the emails
   * @return an integer representing the word count in the unread emails
   * @throws IOException if an issue occurs with the Gmail service
   */
  int getWordCount(List<Message> unreadMessages);
}
