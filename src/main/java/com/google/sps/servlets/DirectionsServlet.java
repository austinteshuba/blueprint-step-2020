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

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.sps.exceptions.DirectionsException;
import com.google.sps.model.DirectionsClient;
import com.google.sps.model.DirectionsClientFactory;
import com.google.sps.model.DirectionsClientImpl;
import com.google.sps.utility.KeyProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves key information from optimizing between addresses. */
@WebServlet("/directions")
public class DirectionsServlet extends HttpServlet {

  private final DirectionsClientFactory directionsClientFactory;
  private final String apiKey;
  private final String origin;
  private final String destination;
  private final List<String> waypoints;

  /**
   * Construct servlet with default DirectionsClient.
   *
   * @throws IOException
   */
  public DirectionsServlet() throws IOException {
    directionsClientFactory = new DirectionsClientImpl.Factory();
    apiKey = (new KeyProvider()).getKey("apiKey");
    origin = "Waterloo, ON";
    destination = "Waterloo, ON";
    waypoints = ImmutableList.of("Montreal, QC", "Windsor, ON", "Kitchener, ON");
  }

  /**
   * Construct servlet with explicit implementation of DirectionsClient.
   *
   * @param factory A DirectionsClientFactory containing the implementation of
   *     DirectionsClientFactory.
   */
  public DirectionsServlet(
      DirectionsClientFactory factory,
      String fakeApiKey,
      String fakeOrigin,
      String fakeDestination,
      List<String> fakeWaypoints) {
    directionsClientFactory = factory;
    apiKey = fakeApiKey;
    origin = fakeOrigin;
    destination = fakeDestination;
    waypoints = fakeWaypoints;
  }

  /**
   * Returns the most optimal order of travel between addresses.
   *
   * @param request HTTP request from the client.
   * @param response HTTP response to the client.
   * @throws ServletException
   */
  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException {
    String queryString = request.getQueryString().replace("%20", " ");
    List<String> splitQueryString = Arrays.asList(queryString.split("&"));
    String demoOrigin = Arrays.asList(splitQueryString.get(0).split("=")).get(1);
    String demoDestination = Arrays.asList(splitQueryString.get(1).split("=")).get(1);
    String demoWaypointsString = Arrays.asList(splitQueryString.get(2).split("=")).get(1);
    List<String> demoWaypoints = demoWaypointsString == "" ? new ArrayList() : Arrays.asList(demoWaypointsString.split(";")); 
    
    try {
      DirectionsClient directionsClient = directionsClientFactory.getDirectionsClient(apiKey);
      List<String> directions = directionsClient.getDirections(demoOrigin, demoDestination, demoWaypoints);
      Gson gson = new Gson();
      String directionsJson = gson.toJson(directions);
      response.setContentType("application/json");
      response.getWriter().println(directionsJson);
    } catch (DirectionsException | IOException e) {
      throw new ServletException(e);
    }
  }
}
