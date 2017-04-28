# Zalenium's Anonymous Aggregate User Behaviour Analytics
Zalenium has begun gathering anonymous aggregate user behaviour analytics and reporting these to Google Analytics. You are notified about this when you start and stop Zalenium.

## Why?
Zalenium is provided free of charge and we don't have direct communication with its users nor time resources to ask directly for their feedback. As a result, we now use anonymous aggregate user analytics to help us understand how Zalenium is being used, the most common used features based on how, where and when people use it. With this information we can prioritize some features over other ones, understand better which Selenium or Docker versions we should support depending on the usage, and get execution exceptions to identify bugs.

## What?
Zalenium's analytics record some shared information for every event:

- The Google Analytics version i.e. `1` (https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#v)
- The Zalenium analytics tracking ID e.g. `UA-88441352` (https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#tid)
- The docker engine ID (which is generated randomly in each docker installation) is used to anonymize each user e.g. `U352:5K7B:GYLE:M3RN:XL4Z:XZNW:63JJ:T3Y4:XYRI:IFDK:FUEF:XIGS`. This is retrieved by getting the `ID:` field after executing `docker info`. This does not allow us to track individual users but does enable us to accurately measure user counts vs. event counts (https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#cid)
- The Google Analytics anonymous IP setting is enabled i.e. `1` (https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#aip)
- The Zalenium application name e.g. `zalenium` (https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#an)
- The Zalenium application version e.g. `zalenium-2.53.1i.jar` (https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#av)
- The Zalenium analytics hit type e.g. `screenview` (https://developers.google.com/analytics/devguides/collection/protocol/v1/parameters#t)

Zalenium's analytics records the following different events:

- a `screenview` when you start Zalenium and the options you used to start it. Users and API keys for the cloud testing providers are never recorded. 
- an `event` hit type with the `test_start` event category, the proxy your test is using to run (docker-selenium or Sauce Labs) as action, and the capabilities as event label.
- an `event` hit type with the `test_stop` event category, the proxy your test is using to run (docker-selenium or Sauce Labs) as action.
- an `exception` hit type with the `exception` event category, exception description of the exception name and whether the exception was fatal e.g. `1`

With the recorded information, it is not possible for us to match any particular real user with the anonymized user ID. For example, if you ever reinstall docker, a new ID is generated and this would count as a completely new anonymized user.

As far as we can tell it would be impossible for Google to match the randomly generated analytics user ID to any other Google Analytics user ID. If Google turned evil the only thing they could do would be to lie about anonymising IP addresses and attempt to match users based on IP addresses.

## When/Where?
Zalenium's analytics are sent throughout Zalenium's execution to Google Analytics over HTTPS.

## Who?
Zalenium's analytics are accessible to Zalenium's current maintainers. Contact [@diemol](https://github.com/diemol) if you are a maintainer and need access.

## How?
The code is viewable in:
* [Zalenium start](./scripts/zalenium.sh#L267)
* [Zalenium stop](./scripts/zalenium.sh#L98)
* [Start, stop tests and exceptions](./src/main/java/de/zalando/ep/zalenium/util/GoogleAnalyticsApi.java)

The code is implemented so it gets executed in a background process, without delaying normal execution. If it fails, it will do it immediately and silently.

## Opting out before starting Zalenium
Zalenium analytics helps us maintainers and leaving it on is appreciated. However, if you want to opt out and not send any information, you can do this by using passing the following parameter at start time:

```sh
--sendAnonymousUsageInfo false
```

## Disclaimer
This document and the implementation are based on the great idea implemented by [Homebrew](https://github.com/Homebrew/brew/blob/master/docs/Analytics.md)