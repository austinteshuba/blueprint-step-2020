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

import com.google.api.client.util.StringUtils;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePart;
import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.util.List;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import javax.mail.MessagingException;

/** Contains business logic to compute the word count in unread emails for the PlanMailServlet */
public final class PlanMailResponseHelperImpl implements PlanMailResponseHelper {

  @Override
  public int getWordCount(List<Message> unreadMessages) {
    int wordCount = 0;
    for (Message message : unreadMessages) {
      wordCount += getMessageSize(message);
    }
    return wordCount;
  }

  /** Private method to get the individual word count of a single email */
  private int getMessageSize(Message message) {
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
