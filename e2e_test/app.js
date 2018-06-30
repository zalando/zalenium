const express = require("express");
const app = express();
const port = 3000;

app.get("/", (req, res) => {
  res.send("<p>Hello, Zalenium!</p>");
});

app.listen(port, () => {
  console.log(`Listening on: ${port}`);
});
