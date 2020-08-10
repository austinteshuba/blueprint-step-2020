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

// Script to handle populating data in the panels

/* eslint-disable no-unused-vars */
/* global signOut, AuthenticationError, Task, getDateInLocalTimeZone,
 encodeListForUrl */

/**
 * Populate Tasks container with user information
 */
function populateTasks() {
  let fetchFrom;
  const tasksSelect = document.querySelector('#tasks-select');
  const goSelect = document.querySelector('#go-select');
  // Cast from HTMLOptionsCollection to Array
  const options = Array(...tasksSelect.options);

  if (options.length === 0) {
    fetchFrom = '/tasks';
  } else {
    const selectedOptions = [];
    options.forEach((option) => {
      if (option.selected) {
        selectedOptions.push(option.value);
      }
    });
    fetchFrom = '/tasks?taskLists=' + selectedOptions.join();
  }

  // Set default values while loading
  const tasksToCompleteElement = document.getElementById('tasks-to-complete');
  const tasksDueTodayElement = document.getElementById('tasks-due-today');
  const tasksCompletedTodayElement =
      document.getElementById('tasks-completed-today');
  const tasksOverdueElement = document.getElementById('tasks-overdue');
  tasksToCompleteElement.innerText = '...';
  tasksDueTodayElement.innerText = '...';
  tasksCompletedTodayElement.innerText = '...';
  tasksOverdueElement.innerText = '...';

  fetch(fetchFrom)
      .then((response) => {
        // If response is a 403, user is not authenticated
        if (response.status === 403) {
          throw new AuthenticationError();
        }
        return response.json();
      })
      .then((tasksResponse) => {
        if (options.length === 0) {
          const taskListIdsToTitles = tasksResponse['taskListIdsToTitles'];
          tasksSelect.innerText = '';
          goSelect.innerText = '';
          for (const taskListId in taskListIdsToTitles) {
            if (Object.prototype
                .hasOwnProperty
                .call(taskListIdsToTitles, taskListId)) {
              let option = document.createElement('option');
              option.value = taskListId;
              option.innerText = taskListIdsToTitles[taskListId];
              tasksSelect.append(option);
              option = document.createElement('option');
              option.value = taskListId;
              option.innerText = taskListIdsToTitles[taskListId];
              goSelect.append(option);
            }
          }
        }
        tasksToCompleteElement
            .innerText = tasksResponse['tasksToCompleteCount'];
        tasksDueTodayElement
            .innerText = tasksResponse['tasksDueTodayCount'];
        tasksCompletedTodayElement
            .innerText = tasksResponse['tasksCompletedTodayCount'];
        tasksOverdueElement
            .innerText = tasksResponse['tasksOverdueCount'];
      })
      .catch((e) => {
        console.log(e);
        if (e instanceof AuthenticationError) {
          signOut();
        }
      });
}

/**
 * Will reset the tasklists selector and populate the panel again,
 * giving the system the chance to add new tasklists to the tasklists options.
 */
function resetTasks() {
  document.querySelector('#tasks-select').options.length = 0;
  populateTasks();
}

/**
 * Populate Calendar container with user's events
 */
function populateCalendar() {
  const calendarContainer = document.querySelector('#calendar');
  fetch('/calendar')
      .then((response) => {
        // If response is a 403, user is not authenticated
        if (response.status === 403) {
          throw new AuthenticationError();
        }
        return response.json();
      })
      .then((hoursJson) => {
        // Display the days and the free hours for each one of them
        const days = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa'];
        const panelContent = document.querySelector('#panel-content');
        panelContent.innerHTML = '';
        for (const day in hoursJson.workTimeFree) {
          if (Object.prototype.hasOwnProperty
              .call(hoursJson.workTimeFree, day)) {
            const panelContentEntry = document.createElement('div');
            panelContentEntry.className = 'panel__content-entry';
            const dayContainer = document.createElement('p');
            dayContainer.className = 'panel__text-icon u-text-calendar';
            dayContainer.innerText =
                days[(hoursJson.startDay + parseInt(day)) % 7];
            const timeContainer = document.createElement('div');
            const workContainer = document.createElement('p');
            workContainer.className = 'u-block';
            workContainer.innerText =
                hoursJson.workTimeFree[day].hours +
                'h ' + hoursJson.workTimeFree[day].minutes +
                'm free (working)';
            const personalContainer = document.createElement('p');
            personalContainer.className = 'u-block';
            personalContainer.innerText =
                hoursJson.personalTimeFree[day].hours +
                'h ' + hoursJson.personalTimeFree[day].minutes +
                'm free (personal)';
            timeContainer.appendChild(workContainer);
            timeContainer.appendChild(personalContainer);
            panelContentEntry.appendChild(dayContainer);
            panelContentEntry.appendChild(timeContainer);
            panelContent.appendChild(panelContentEntry);
          }
        }
      })
      .catch((e) => {
        console.log(e);
        if (e instanceof AuthenticationError) {
          signOut();
        }
      });
}

/**
 * Post a new task to a given taskList
 *
 * @param {string} taskListId the id of the taskList that the new task should
 *     belong to
 * @param {Task} taskObject valid Task object
 * @return {Promise<any>} A promise that is resolved once the task is
 *     posted
 */
function postNewTask(taskListId, taskObject) {
  const taskJson = JSON.stringify(taskObject);

  const newTaskRequest =
      new Request(
          '/tasks?taskListId=' + taskListId,
          {method: 'POST', body: taskJson}
      );

  return fetch(newTaskRequest)
      .then((response) => {
        switch (response.status) {
          case 200:
            resetTasks();
            return response.json();
          case 403:
            throw new AuthenticationError();
          default:
            throw new Error(response.status + ' ' + response.statusText);
        }
      });
}

/**
 * Update the tasks and taskLists lists.
 *
 * @return {Promise<any>} A promise that is resolved once the tasks and
 *     and taskLists arrays are updated, and rejected if there's an error
 */
function getTaskListsAndTasks() {
  return fetch('/taskLists')
      .then((response) => {
        switch (response.status) {
          case 200:
            return response.json();
          case 403:
            throw new AuthenticationError();
          default:
            throw new Error(response.status + ' ' + response.statusText);
        }
      })
      .then((response) => {
        return response;
      });
}

/**
 * Post a new taskList to the server
 *
 * @param {string} title title of new taskList.
 * @return {Promise<any>} A promise that is resolved once the taskList is
 *     posted
 */
function postNewTaskList(title) {
  const newTaskListRequest =
      new Request(
          '/taskLists?taskListTitle=' + title,
          {method: 'POST'}
      );

  return fetch(newTaskListRequest)
      .then((response) => {
        switch (response.status) {
          case 200:
            resetTasks();
            return response.json();
          case 403:
            throw new AuthenticationError();
          default:
            throw new Error(response.status + ' ' + response.statusText);
        }
      })
      .then((taskListObject) => {
        return taskListObject;
      });
}

/**
 * Populate Go container
 */
function populateGo() {
  const goContainer = document.querySelector('#go');

  const goSelect = document.querySelector('#go-select');
  const origin = document.querySelector('#go-origin').value;
  const destination = document.querySelector('#go-destination').value;

  // Cast from HTMLOptionsCollection to Array
  const options = Array(...goSelect.options);

  const selectedOptions = [];
  options.forEach((option) => {
    if (option.selected) {
      selectedOptions.push(option.value);
    }
  });

  goContainer.innerText = '• • •';

  fetch('/go?taskLists=' + selectedOptions.join() +
          '&origin=' + origin +
          '&destination=' + destination)
      .then((response) => {
        // If response is a 403, user is not authenticated
        if (response.status === 403) {
          throw new AuthenticationError();
        }
        return response.json();
      })
      .then((legs) => {
        goContainer.innerText = '';
        legs.forEach((leg) => {
          const li = document.createElement('li');
          li.innerText = leg;
          goContainer.append(li);
        });
      })
      .catch((e) => {
        console.log(e);
        if (e instanceof AuthenticationError) {
          signOut();
        }
      });
}

/**
 * Populate Plan-mail panel with potential times to read the emails
 */
function populatePlanMail() {
  const planContainer = document.querySelector('#plan');
  fetch('/plan-mail?summary=Read emails')
      .then((response) => {
        // If response is a 403, user is not authenticated
        if (response.status === 403) {
          throw new AuthenticationError();
        }
        return response.json();
      })
      .then((planMailResponse) => {
        // Display the potential times to create events
        document.querySelector('#word-count').innerText =
            planMailResponse.wordCount;
        document.querySelector('#average-reply').innerText =
            planMailResponse.averageReadingSpeed;
        document.querySelector('#time-needed').innerText =
            planMailResponse.minutesToRead;
        const messageEventContainer = document.querySelector('#message-event');
        messageEventContainer.innerHTML = '';
        const intervalContainer = document.querySelector('#free-interval');
        intervalContainer.innerHTML = '';
        if (planMailResponse.potentialEventTimes.length === 0) {
          messageEventContainer.innerText = 'No new events needed';
        } else {
          messageEventContainer.innerText = 'Click to schedule';
          for (const index in planMailResponse.potentialEventTimes) {
            if (Object.prototype.hasOwnProperty
                .call(planMailResponse.potentialEventTimes, index)) {
              const buttonElement = document.createElement('button');
              buttonElement.className = 'button plan__button';
              buttonElement.innerText =
                `${planMailResponse.potentialEventTimes[index].start} to \
                ${planMailResponse.potentialEventTimes[index].end}`;
              buttonElement.setAttribute('start',
                  planMailResponse.potentialEventTimes[index].start);
              buttonElement.setAttribute('end',
                  planMailResponse.potentialEventTimes[index].end);
              buttonElement.addEventListener('click', () => {
                createEvent(buttonElement.getAttribute('start'),
                    buttonElement.getAttribute('end'));
              });
              intervalContainer.appendChild(buttonElement);
            }
          }
        }
      })
      .catch((e) => {
        console.log(e);
        if (e instanceof AuthenticationError) {
          signOut();
        }
      });
}

/**
 * Call a post request to create a new event in the calendar, then display the
 * updated information in calendar and plan panels
 *
 * @param {string} eventStart the start time of the event to create
 * @param {string} eventEnd the end time of the event to create
 */
function createEvent(eventStart, eventEnd) {
  const params = new URLSearchParams();
  params.append('start', eventStart);
  params.append('end', eventEnd);
  params.append('summary', 'Read emails');
  params.append('id', 'primary');
  fetch('/calendar', {method: 'POST', body: params})
      .then((response) => {
        populateCalendar();
        populatePlanMail();
      })
      .catch((e) => {
        console.log(e);
      });
}

/**
 * Set up the assign panel. For now, this just prints the response
 * from the server for /gmail-actionable-emails
 */
function setUpAssign() {
  const assignContent = document.querySelector('#assign');

  const subjectLinePhrases = ['Action Required', 'Action Requested'];
  const unreadOnly = true;
  const nDays = 7;
  fetchActionableEmails(subjectLinePhrases, unreadOnly, nDays)
      .then((response) => {
        assignContent.innerText = response
            .map((obj) => obj.subject)
            .join('\n');
      })
      .catch((e) => {
        console.log(e);
        if (e instanceof AuthenticationError) {
          signOut();
        }
      });
}

/**
 * Get actionable emails from server. Used for assign panel
 *
 * @param {string[]} listOfPhrases list of words/phrases that the subject line
 *     of user's emails should be queried for
 * @param {boolean} unreadOnly true if only unread emails should be returned,
 *     false otherwise
 * @param {number} nDays number of days to check unread emails for.
 *     Should be an integer > 0
 * @return {Promise<Object>} returns promise that returns the JSON response
 *     from client. Should be list of Gmail Message Objects. Will throw
 *     AuthenticationError in the case of a 403, or generic Error in
 *     case of other error code
 */
function fetchActionableEmails(listOfPhrases, unreadOnly, nDays) {
  const listOfPhrasesString = encodeListForUrl(listOfPhrases);
  const unreadOnlyString = unreadOnly.toString();
  const nDaysString = nDays.toString();

  const queryString =
      `/gmail-actionable-emails?subjectLinePhrases=${listOfPhrasesString}` +
      `&unreadOnly=${unreadOnlyString}&nDays=${nDaysString}`;

  return fetch(queryString)
      .then((response) => {
        switch (response.status) {
          case 200:
            return response.json();
          case 403:
            throw new AuthenticationError();
          default:
            throw new Error(response.status + ' ' + response.statusText);
        }
      });
}
