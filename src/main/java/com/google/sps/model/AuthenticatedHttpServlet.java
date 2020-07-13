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
import com.google.sps.utility.AuthenticationUtility;
import java.io.IOException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** HttpServlet that enforces the verification of user tokens */
public abstract class AuthenticatedHttpServlet extends HttpServlet {
  // Error message if user is not authenticated
  protected static final String ERROR_403 = "Authentication tokens not present / invalid";

  /**
   * Verifies user credentials on GET (sending a 403 error in the case that the user is not properly
   * authenticated).
   *
   * @param request Http request sent from client
   * @param response Http response to be sent back to the client
   * @throws IOException if there is an issue processing the request
   */
  @Override
  public final void doGet(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Credential googleCredential = loadCredential(request, response);
    if (googleCredential != null) {
      doGet(request, response, googleCredential);
    }
  }

  /**
   * Verifies user credentials on POST (sending a 403 error in the case that the user is not
   * properly authenticated).
   *
   * @param request Http request sent from client
   * @param response Http response to be sent back to the client
   * @throws IOException if there is an issue processing the request
   */
  @Override
  public final void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Credential googleCredential = loadCredential(request, response);
    if (googleCredential != null) {
      doPost(request, response, googleCredential);
    }
  }

  /**
   * Handle GET request
   *
   * @param request HTTP request from client
   * @param response Http response to be sent to client
   * @param googleCredential valid, verified google credential object
   * @throws IOException is there is an issue processing the request
   */
  public void doGet(
      HttpServletRequest request, HttpServletResponse response, Credential googleCredential)
      throws IOException {
    response.sendError(400, "GET is not supported");
  }

  /**
   * Handle POST request
   *
   * @param request HTTP request from client
   * @param response Http response to be sent to client
   * @param googleCredential valid, verified google credential object
   * @throws IOException is there is an issue processing the request
   */
  public void doPost(
      HttpServletRequest request, HttpServletResponse response, Credential googleCredential)
      throws IOException {
    response.sendError(400, "POST is not supported");
  }

  /**
   * Get credential, or return 403 error if the credential is invalid
   *
   * @param request HttpRequest from client
   * @param response Response to send to client
   * @throws IOException if an issue occurs processing the response
   */
  private Credential loadCredential(HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    Credential googleCredential = AuthenticationUtility.getGoogleCredential(request);
    if (googleCredential == null) {
      response.sendError(403, ERROR_403);
    }

    return googleCredential;
  }
}
