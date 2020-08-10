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

// This file contains functionality relating to Google Sign in
// and storing cookies

// This application uses Google sign-in to authenticate and authorize users.
//     The flow looks like this (all functions found in `auth.js`):
//
// 1) `init()` is called when the page loads.
// 2) The OAuth ClientID is retrieved from the server
// 3) The Google sign-in button is loaded from the
//    Google Platform Library
//    (https://github.com/google/google-api-javascript-client)
// 4) The button is configured to collect the proper permissions
//    (read access to Gmail, read/write access to Tasks, read/write access to
//    Calendar), and to call `onSignIn()` when the user successfully
//    authenticates. Note this is automatically called if the user is already
//    signed in (this is handled automatically by the Platform library)
// 5) `onSignIn()` is called, and stores two cookies on the client:
//    `idToken` and `accessToken`.
//    These are used to authenticate and authorize users, respectively.
//    The UI then appears, and the sign in button is removed.
// 6) When the user signs out, `onSignOut()` is called and the cookies are
//    deleted. The UI then disappears and the Google sign-in button reappears.

/* eslint-disable no-unused-vars */
/* global gapi, addCookie, isCookiePresent,
deleteCookie, populateGmail, populateTasks, populateCalendar,
postAndGetTaskList, populateGo, populatePlanMail,setUpAssign*/
// TODO: Handle CommonJS (Issue #31)

/**
 * Function called when script https://apis.google.com/js/platform.js loads
 * Initializes the Google Sign-In with a client ID hosted on the server
 * and renders the button on the page
 */
function init() {
  gapi.load('auth2', () => {
    fetch('/client-id')
        .then((response) => response.json())
        .then((clientIdObject) => {
          const clientId = clientIdObject['client-id'];
          // Initialize Google Sign-in with the clientID
          gapi.auth2.init({
            'client_id': clientId,
          }).then(() => {
            renderButton();
          });
        });
  });
}

/**
 * Will initialize the page based on the current authentication state
 */
function handleAuthenticationState() {
  const featureContainer = document.querySelector('.feature-container');
  const signInButton = document.querySelector('#google-sign-in-btn');
  if (isCookiePresent('idToken')) {
    console.log('Fetching Data for panels');

    // User is logged in.
    // Hide sign in button, show features
    signInButton.setAttribute('hidden', '');
    featureContainer.removeAttribute('hidden');

    // Populate information panels at top of dashboard
    populateGmail();
    populateTasks();
    populateCalendar();
    // TEMPORARY: Commenting this out since it adds tasks to user's
    // Tasks account on every run.
    // postAndGetTaskList();

    // Populate magic feature panels at the bottom of the dashboard
    populatePlanMail();
    setUpAssign();
  } else {
    // User is not logged in.
    // Show sign in button, hide features
    signInButton.removeAttribute('hidden');
    featureContainer.setAttribute('hidden', '');
  }
}

/**
 * Called when user signs in using rendered sign in button
 * @param {Object} googleUser object that contains information about
 *     authenticated user.
 */
function onSignIn(googleUser) {
  console.log('Signing in with Google');

  // Get the authentication object. Always include accessID, even if null
  const userAuth = googleUser.getAuthResponse(true);

  const idToken = userAuth.id_token;
  const accessToken = userAuth.access_token;
  const expiry = userAuth.expires_at;
  const expiryUtcTime = new Date(expiry).toUTCString();

  // Set cookie that contains idToken to authenticate the user
  // Will automatically delete when the access token expires
  // or when the browser is closed
  addCookie('idToken', idToken, expiryUtcTime);

  // Set cookie that contains idToken to authenticate the user
  // Will automatically delete when the access token expires
  // or when the browser is closed
  addCookie('accessToken', accessToken, expiryUtcTime);

  handleAuthenticationState();
}

/**
 * Signs out of Google Account
 */
function signOut() {
  const auth2 = gapi.auth2.getAuthInstance();
  auth2.signOut().then(() => {
    console.log('Signing out with Google');

    // Remove Authentication Cookies
    deleteCookie('idToken');
    deleteCookie('accessToken');

    handleAuthenticationState();
  });
}

/**
 * Function to render a UI element included in the Google Sign-In for websites
 * package. See documentation:
 * https://developers.google.com/identity/sign-in/web/reference#gapisignin2renderid_options
 * Request readonly access to Gmail, Tasks, and Calendar
 */
function renderButton() {
  gapi.signin2.render('google-sign-in-btn', {
    'scope': 'https://www.googleapis.com/auth/gmail.readonly ' +
        'https://www.googleapis.com/auth/calendar ' +
        'https://www.googleapis.com/auth/tasks',
    'width': 240,
    'height': 40,
    'longtitle': true,
    'theme': 'dark',
    'onsuccess': onSignIn,
    'onfailure': () => {},
  });
}

/**
 * Custom error type to handle cases where user is not authenticated
 */
class AuthenticationError extends Error {}
