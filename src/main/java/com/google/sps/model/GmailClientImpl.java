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
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import com.google.sps.exceptions.GmailException;
import com.google.sps.utility.ServletUtility;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Handles GET requests from Gmail API */
public class GmailClientImpl implements GmailClient {
  private Gmail gmailService;
  private static final int BATCH_REQUEST_CALL_LIMIT = 100;
  private static final int MAXIMUM_BACKOFF_SECONDS = 32;

  private GmailClientImpl(Credential credential) {
    JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
    HttpTransport transport = UrlFetchTransport.getDefaultInstance();
    String applicationName = ServletUtility.APPLICATION_NAME;

    gmailService =
        new Gmail.Builder(transport, jsonFactory, credential)
            .setApplicationName(applicationName)
            .build();
  }

  /**
   * List the messages in a user's Gmail account that match the passed query.
   *
   * @param query search query to filter which results are returned (see:
   *     https://support.google.com/mail/answer/7190?hl=en)
   * @return list of message objects that have an ID and thread ID
   * @throws IOException if an issue occurs with the gmail service
   */
  @Override
  public List<Message> listUserMessages(String query) throws IOException {
    // Results are returned in batches of 100 from the Gmail API, though a next page token is
    // included if another batch still exists. This method will use these tokens to get all of the
    // emails that match the query, even if that is in excess of 100.
    List<Message> userMessages = new ArrayList<>();
    String nextPageToken = null;

    do {
      ListMessagesResponse response =
          gmailService
              .users()
              .messages()
              .list("me")
              .setQ(query)
              .setPageToken(nextPageToken)
              .execute();
      List<Message> newBatchUserMessages = response.getMessages();
      if (newBatchUserMessages == null) {
        break;
      }

      nextPageToken = response.getNextPageToken();
      userMessages.addAll(newBatchUserMessages);
    } while (nextPageToken != null);

    return userMessages;
  }

  @Override
  public Message getUserMessage(String messageId, MessageFormat format) throws IOException {
    Message message =
        gmailService
            .users()
            .messages()
            .get("me", messageId)
            .setFormat(format.formatValue)
            .execute();

    return message;
  }

  @Override
  public Message getUserMessageWithMetadataHeaders(String messageId, List<String> metadataHeaders)
      throws IOException {
    Message message =
        gmailService
            .users()
            .messages()
            .get("me", messageId)
            .setFormat(MessageFormat.METADATA.formatValue)
            .setMetadataHeaders(metadataHeaders)
            .execute();

    return message;
  }

  @Override
  public List<Message> getUnreadEmailsFromNDays(GmailClient.MessageFormat messageFormat, int nDays)
      throws IOException {
    String ageQuery = GmailClient.emailAgeQuery(nDays, "d");
    String unreadQuery = GmailClient.unreadEmailQuery(true);
    String searchQuery = GmailClient.combineSearchQueries(ageQuery, unreadQuery);

    return listUserMessagesWithFormat(searchQuery, messageFormat);
  }

  @Override
  public List<Message> getActionableEmails(
      List<String> subjectLinePhrases, boolean unreadOnly, int nDays, List<String> metadataHeaders)
      throws IOException {
    String ageQuery = GmailClient.emailAgeQuery(nDays, "d");
    String unreadQuery = GmailClient.unreadEmailQuery(unreadOnly);
    String subjectLineQuery = GmailClient.oneOfPhrasesInSubjectLineQuery(subjectLinePhrases);
    String searchQuery = GmailClient.combineSearchQueries(ageQuery, unreadQuery, subjectLineQuery);

    return listUserMessagesWithFormat(searchQuery, MessageFormat.METADATA, metadataHeaders);
  }

  /**
   * Lists out messages, but maps each user message to a specific message format. Uses batching,
   * where there is a limit of 100 calls per batch request.
   *
   * @param searchQuery search query to filter which results are returned (see:
   *     https://support.google.com/mail/answer/7190?hl=en)
   * @param messageFormat GmailClient.MessageFormat setting that specifies how much information from
   *     each email to retrieve
   * @return list of messages with requested information
   * @throws IOException if there is an issue with the GmailService
   */
  private List<Message> listUserMessagesWithFormat(
      String searchQuery, GmailClient.MessageFormat messageFormat) throws IOException {

    return listUserMessagesWithFormat(searchQuery, messageFormat, null);
  }

  /**
   * Lists out messages, but maps each user message to a specific message format Uses batching,
   * where there is a limit of 100 calls per batch request.
   *
   * @param messageFormat GmailClient.MessageFormat setting that specifies how much information from
   *     each email to retrieve
   * @param searchQuery search query to filter which results are returned (see:
   *     https://support.google.com/mail/answer/7190?hl=en)
   * @param metadataHeaders list of names of headers (e.g. "From") that should be included
   * @return list of messages with requested information
   * @throws IOException if there is an issue with the GmailService
   */
  private List<Message> listUserMessagesWithFormat(
      String searchQuery, GmailClient.MessageFormat messageFormat, List<String> metadataHeaders)
      throws IOException {
    List<Message> userMessagesWithoutFormat = listUserMessages(searchQuery);
    List<Message> userMessagesWithFormat = new ArrayList<>();
    List<Message> batchMessagesWithFormat = new ArrayList<>();

    // When each message is retrieved, add them to the batchMessagesWithFormat list
    // If the entire batch request is successful, the messages will be added to the
    // userMessagesWithFormat list.
    // This is to prevent duplicates in the case of a 429 error.
    JsonBatchCallback<Message> batchCallback = addToListMessageCallback(batchMessagesWithFormat);

    // Add messages to a batch request, BATCH_REQUEST_CALL_LIMIT messages at a time
    int messageIndex = 0;
    while (messageIndex < userMessagesWithoutFormat.size()) {
      BatchRequest batchRequest = gmailService.batch();

      while (messageIndex < userMessagesWithoutFormat.size()
          && batchRequest.size() < BATCH_REQUEST_CALL_LIMIT) {
        Message currentMessage = userMessagesWithoutFormat.get(messageIndex);
        gmailService
            .users()
            .messages()
            .get("me", currentMessage.getId())
            .setFormat(messageFormat.formatValue)
            .setMetadataHeaders(metadataHeaders)
            .queue(batchRequest, batchCallback);
        messageIndex++;
      }

      executeGmailBatchRequest(batchRequest, batchMessagesWithFormat);
      userMessagesWithFormat.addAll(batchMessagesWithFormat);
      batchMessagesWithFormat.clear();
    }

    return userMessagesWithFormat;
  }

  /**
   * Will execute a gmail batch request. In the case of 429 failure, will retry the request using
   * standard exponential backoff.
   *
   * @param request the request to be executed. Should throw GmailException in the case of failure
   * @param callbackList the list that contains the objects from successful responses in the batch
   *     request. Cleared in the case of failure to prevent duplicate entries
   * @param backoff the current backoff in seconds. Will be increased exponentially until maximum
   *     backoff is reached
   * @throws IOException if there is a read/write issue with the request
   */
  private void executeGmailBatchRequest(BatchRequest request, List callbackList, int backoff)
      throws IOException {
    try {
      request.execute();
    } catch (GmailException e) {
      // Only handle errors with code 403 or 429. Only handle 403 errors if they pertain to
      // usageLimits (other 403
      // errors [e.g. authentication errors] should be floated up)
      GoogleJsonError googleJsonError = e.getGoogleJsonError().orElseThrow(() -> e);
      if (googleJsonError.getCode() != 429 && googleJsonError.getCode() != 403
          || backoff * 2 > MAXIMUM_BACKOFF_SECONDS) {
        throw e;
      }
      if (googleJsonError.getCode() == 403
          && !googleJsonError.getErrors().get(0).getDomain().equals("usageLimits")) {
        throw e;
      }

      callbackList.clear();
      try {
        TimeUnit.SECONDS.sleep(backoff);
      } catch (InterruptedException ex) {
        throw new RuntimeException(ex);
      }
      System.out.println(String.format("Backoff: %d", backoff * 2));
      executeGmailBatchRequest(request, callbackList, backoff * 2);
    }
  }

  /**
   * Will execute a gmail batch request. In the case of 429 failure, will retry the request using
   * standard exponential backoff. Default initial backoff is 1 second.
   *
   * @param request the request to be executed. Should throw GmailException in the case of failure
   * @param callbackList the list that contains the objects from successful responses in the batch
   *     request. Cleared in the case of failure to prevent duplicate entries
   * @throws IOException if there is a read/write issue with the request
   */
  private void executeGmailBatchRequest(BatchRequest request, List callbackList)
      throws IOException {
    executeGmailBatchRequest(request, callbackList, 1);
  }

  /**
   * Will create a callback function for a batch request that adds a message to a specified list in
   * the case of success, or throws a GmailException in the case of failure
   *
   * @param listToAddTo a reference to a list the message should be added to
   * @return a callback that can be used in a batch request to add a message to the specified list
   * @throws GmailException if a GoogleJsonError arises while processing the request
   */
  private JsonBatchCallback<Message> addToListMessageCallback(List<Message> listToAddTo) {
    return new JsonBatchCallback<Message>() {
      @Override
      public void onFailure(GoogleJsonError googleJsonError, HttpHeaders httpHeaders) {
        System.out.println(googleJsonError.getMessage() + " " + googleJsonError.getCode());
        throw new GmailException(googleJsonError.getMessage(), googleJsonError);
      }

      @Override
      public void onSuccess(Message message, HttpHeaders httpHeaders) {
        listToAddTo.add(message);
      }
    };
  }

  /** Factory to create a GmailClientImpl instance with given credential */
  public static class Factory implements GmailClientFactory {
    /**
     * Create a GmailClientImpl instance
     *
     * @param credential Google credential object
     * @return GmailClientImpl instance with credential
     */
    @Override
    public GmailClient getGmailClient(Credential credential) {
      return new GmailClientImpl(credential);
    }
  }
}
