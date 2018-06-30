const assert = require("assert");

describe("Application", () => {
  it("should be reachable by Zalenium.", () => {
    browser.url("/");
    assert($("p").getText() === "Hello, Zalenium!");
  });
});
