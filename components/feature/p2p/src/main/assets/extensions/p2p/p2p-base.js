/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this file,
 * You can obtain one at http://mozilla.org/MPL/2.0/. */

let fd = require('freeze-dry');
let port = browser.runtime.connectNative("mozacP2P");

port.onMessage.addListener((message) => {
  console.log("Hooray! A message arrived for me!");
  console.log(`Message type: #{message.action}`);
  switch (message.action) {
    case 'get_html':
      var promise = fd.default();
      promise.then(function(result) {
        port.postMessage(result);
      });
      break;
      default:
        console.log("I do not know how to handle this action: ${message.action}")
    }
});

port.onDisconnect.addListener((p) => {
  if (p.error) {
    console.log("Wah! Disconnected due to an error: ${p.error.message}");
  } else {
    console.log("Disconnected properly")
  }
});

window.addEventListener("unload", (event) => { console.log("Time to disconnect. Bye!"); port.disconnect() }, false);