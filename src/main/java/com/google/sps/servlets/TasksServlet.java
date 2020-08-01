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

package com.google.sps.servlets;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.tasks.model.Task;
import com.google.api.services.tasks.model.TaskList;
import com.google.gson.Gson;
import com.google.sps.model.AuthenticatedHttpServlet;
import com.google.sps.model.AuthenticationVerifier;
import com.google.sps.model.TasksClient;
import com.google.sps.model.TasksClientFactory;
import com.google.sps.model.TasksClientImpl;
import com.google.sps.utility.JsonUtility;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves selected information from the User's Tasks Account. */
@WebServlet("/tasks")
public class TasksServlet extends AuthenticatedHttpServlet {
  private final TasksClientFactory tasksClientFactory;

  /** Create servlet with default TasksClient and Authentication Verifier implementations */
  public TasksServlet() {
    tasksClientFactory = new TasksClientImpl.Factory();
  }

  /**
   * Create servlet with explicit implementations of TasksClient and AuthenticationVerifier
   *
   * @param authenticationVerifier implementation of AuthenticationVerifier
   * @param tasksClientFactory implementation of TasksClientFactory
   */
  public TasksServlet(
      AuthenticationVerifier authenticationVerifier, TasksClientFactory tasksClientFactory) {
    super(authenticationVerifier);
    this.tasksClientFactory = tasksClientFactory;
  }

  /**
   * Returns all tasks from the user's Tasks account
   *
   * @param request Http request from client. Should contain idToken and accessToken
   * @param response 403 if user is not authenticated, list of Tasks otherwise
   * @param googleCredential a valid google credential object (already verified)
   * @throws IOException if an issue arises while processing the request
   */
  @Override
  public void doGet(
      HttpServletRequest request, HttpServletResponse response, Credential googleCredential)
      throws IOException {
    assert googleCredential != null
        : "Null credentials (i.e. unauthenticated requests) should already be handled";

    TasksClient tasksClient = tasksClientFactory.getTasksClient(googleCredential);
    List<TaskList> allTaskLists = tasksClient.listTaskLists();

    List<Task> allTasks = new ArrayList<>();
    for (TaskList taskList : allTaskLists) {
      allTasks.addAll(tasksClient.listTasks(taskList));
    }

    JsonUtility.sendJson(response, allTasks);
  }

  /**
   * Add a new task object to a tasklist
   *
   * @param request HTTP request from client. Must contain a taskListId. Body must contain a task
   *     entity (https://developers.google.com/tasks/v1/reference/tasks)
   * @param response Http response to be sent to client
   * @param googleCredential valid, verified google credential object
   * @throws IOException if an issue occurs with the tasksService
   */
  @Override
  public void doPost(
      HttpServletRequest request, HttpServletResponse response, Credential googleCredential)
      throws IOException {
    assert googleCredential != null
        : "Null credentials (i.e. unauthenticated requests) should already be handled";
    TasksClient tasksClient = tasksClientFactory.getTasksClient(googleCredential);

    String taskListId = request.getParameter("taskListId");

    if (taskListId == null || taskListId.equals("")) {
      response.sendError(400, "taskListId must be present in request");
      return;
    }

    Gson gson = new Gson();
    Task taskToPost = gson.fromJson(request.getReader(), Task.class);

    // Check if passed task is present and valid
    if (taskToPost == null || taskToPost.isEmpty()) {
      response.sendError(400, "Task body must be non-empty");
      return;
    } else if (!(taskToPost.containsKey("title")
        || taskToPost.containsKey("notes")
        || taskToPost.containsKey("due"))) {
      response.sendError(400, "Task invalid. Task must contain at least one of: title, notes, due");
      return;
    }

    try {
      JsonUtility.sendJson(response, tasksClient.postTask(taskListId, taskToPost));
    } catch (IOException e) {
      throw new IOException(
          "There was an issue posting the task. Check the taskListId and try again", e);
    }
  }
}
