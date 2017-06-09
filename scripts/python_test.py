#!/usr/bin/env python
# -*- coding: utf-8 -*-

# Dependencies:
# pip install --user selenium==3.3.1
# pip install --user retrying>=1.3.3
import time
import os
from retrying import retry

# Import the Selenium 2 namespace (aka "webdriver")
from selenium import webdriver
from selenium.webdriver.common.keys import Keys
from selenium.webdriver.common.desired_capabilities import DesiredCapabilities

import argparse
parser = argparse.ArgumentParser(description='Perform some basic selenium tests.')
parser.add_argument('browser', choices=['chrome', 'firefox'], nargs='?', default='chrome',
                    help='in which browser to test')
args = parser.parse_args()

# http://selenium-python.readthedocs.org/en/latest/api.html
if args.browser == 'chrome':
    caps = DesiredCapabilities.CHROME
elif args.browser == 'firefox':
    caps = DesiredCapabilities.FIREFOX
else:
    raise ValueError("Invalid browser '%s'" % args.browser)

msleep = float( os.environ.get('TEST_SLEEPS', '0.1') )

# http://selenium-python.readthedocs.org/en/latest/api.html
sel_proto = os.environ.get('SELENIUM_HUB_PROTO','http')
sel_host = os.environ.get('SELENIUM_HUB_HOST','localhost')
sel_port = os.environ.get('SELENIUM_HUB_PORT','4444')
myselenium_base_url = "%s://%s:%s" % (sel_proto, sel_host, sel_port)
myselenium_grid_console_url = "%s/grid/console" % (myselenium_base_url)
myselenium_hub_url = "%s/wd/hub" % (myselenium_base_url)
print ("%s      | Will use browser=%s" % (args.browser, args.browser))
print ("%s      | Will sleep '%s' secs between test steps" % (args.browser, msleep))

@retry(stop_max_attempt_number=12, stop_max_delay=30100, wait_fixed=300)
def webdriver_connect():
    print ("%s      | Will connect to selenium at %s" % (args.browser, myselenium_hub_url))
    # http://selenium-python.readthedocs.org/en/latest/getting-started.html#using-selenium-with-remote-webdriver
    return webdriver.Remote(command_executor=myselenium_hub_url, desired_capabilities=caps)

driver = webdriver_connect()
driver.implicitly_wait(10)
time.sleep(msleep)

# Set location top left and size to max allowed on the container
width = os.environ.get('SCREEN_WIDTH','800')
height = os.environ.get('SCREEN_HEIGHT','600')
driver.set_window_position(0, 0)
driver.set_window_size(width, height)

# Test: https://code.google.com/p/chromium/issues/detail?id=519952
# e.g. pageurl = "http://localhost:8080/adwords"
# e.g. pageurl = "http://www.google.com:80/adwords"
# e.g. pageurl = "https://www.google.com:443/adwords"
# e.g. pageurl = "http://d.host.loc.dev:8080/adwords"
page_port = os.environ.get('MOCK_SERVER_PORT','8080')
page_host = os.environ.get('MOCK_SERVER_HOST','mock')
pageurl = ("http://%s:%s/adwords" % (page_host, page_port))

@retry(stop_max_attempt_number=7, stop_max_delay=20100, wait_fixed=300)
def open_web_page():
    print ("%s      | Opening page %s" % (args.browser, pageurl))
    driver.get(pageurl)
    time.sleep(msleep)
    print ("%s      | Current title: %s" % (args.browser, driver.title))
    print ("%s      | Asserting 'Google Adwords' in driver.title" % args.browser)
    assert "Google AdWords | Pay-per-Click-Onlinewerbung auf Google (PPC)" in driver.title

open_web_page()

@retry(stop_max_attempt_number=7, stop_max_delay=20100, wait_fixed=300)
def click_link_costen():
    print ("%s      | Click link 'Kosten'" % args.browser)
    link = driver.find_element_by_link_text('Kosten')
    link.click()
    time.sleep(msleep)

click_link_costen()

driver.maximize_window()

@retry(stop_max_attempt_number=7, stop_max_delay=20100, wait_fixed=300)
def open_costs_page():
    print ("%s      | Current title: %s" % (args.browser, driver.title))
    print ("%s      | Asserting 'Kosten' in driver.title" % args.browser)
    assert "Kosten von Google AdWords | Google AdWords" in driver.title

open_costs_page()

print ("%s      | Go back to home page" % args.browser)
link = driver.find_element_by_link_text('Übersicht')
link.click()
time.sleep(msleep)
print ("%s      | Current title: %s" % (args.browser, driver.title))
print ("%s      | Asserting 'Google (PPC)' in driver.title" % args.browser)
assert "Google AdWords | Pay-per-Click-Onlinewerbung auf Google (PPC)" in driver.title
time.sleep(msleep)

print ("%s      | Close driver and clean up" % args.browser)
driver.close()
time.sleep(msleep)

print ("%s      | All done. SUCCESS!" % args.browser)
try:
    driver.quit()
except:
    pass
