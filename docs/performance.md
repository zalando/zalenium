# A note on Zalenium's perfomance

As Zalenium gained more users over time, it got used in more diverse ways and not only as a small disposable grid to run a few tests.
These new scenarios showed us some opportunities to improve Zalenium's performance. Thanks to our users' feedback, and also from issue 
[#116](https://github.com/zalando/zalenium/issues/116), we decided to work on this topic and to give some tips/guidelines on its usage.

The following data is the result of running a simple test many times in different combinations of threads and hardware specifications. 
A simple test is one that just opens the browser, goes to any given url (e.g. www.google.com), and closes the browser. **The purpose 
of this data is to serve as a guide when you want to setup your tests using Zalenium, these values are not written on stone and might
be different in your environment. Again, please take this just as a guide.** 

*This is a live document and PRs are welcome to add data and comments based on your findings in your environment.*

### Execution times

| Environment |   # of Tests   |   Threads  |   Avg. Time VR** ON   |   Avg. Time VR** OFF   |
|:-----------:|:--------------:|:----------:|:---------------------:|:----------------------:|
|      1      |        50      |      4     |       ~8min 30sec     |        ~5min 15sec     |
|      1      |        50      |      6     |       ~7min 15sec     |        ~4min 30sec     |
|      1      |        50      |      8     |           X*          |        ~4min 15sec     |
|      1      |        50      |      10    |           X*          |        ~4min 45sec     |
|      2      |        30      |      6     |         ~3min         |          ~2min         |
|      2      |        35      |      8     |         ~4min         |          ~2min         |
|      2      |        50      |      10    |         ~5min         |          ~4min         |
|      2      |        60      |      12    |         ~6min         |          ~8min         |

X*=The containers were not stable enough in order to avoid affecting the tests. VR**=Video recording

When no more combinations are shown for a given environment, it means that it was not possible to execute the tests with more threads
in a consistent way.

Tests were executed when the machine had only docker, Zalenium, and the terminal running.  

### Environments

| ID |        Hardware          |    Processor    |     RAM    |           OS         |                    Docker               |
|:--:|:------------------------:|:---------------:|:----------:|:--------------------:|:---------------------------------------:|
| 1  | MacBook Pro (Early 2015) | 2,7GHz 2vCPU i5 | 16 GB DDR3 | macOS Sierra 10.12.5 | Version 17.03.1-ce-mac12 (4 CPUs, 16GB) |
| 2  | Asus N550JV              | 2,7GHz 4vCPU i7 | 16 GB DDR3 | Ubuntu 16.04         | Version 17.05.0                         |


### Findings based on the previous test executions

* Having video recording consumes lots of CPU, which affects the fast creation of new containers, and this becomes more evident when
the amount of threads is increased.
* Disabling video recording allows to run more tests concurrently, but also a limit is hit when tests are executed in 10-12 threads.
Having 10-12 threads does not mean that exactly 10-12 containers are running in that moment. Since tests are finishing at different
times, new containers are created for new tests, therefore around 16-18 containers are running at the same time. Going above those
numbers made the docker deamon and the Selenium Grid slower.
* Starting Zalenium with a given number of Chrome and Firefox nodes didn't make a big difference in the overall execution time because
new containers need to be created when the first ones are disposed. It would only make a difference in a scenario where, for example,
you want to run 10 tests in parallel and start Zalenium with 10 containers (tests would get executed right away and no more containers
are created).

### Jenkins on c3.4xlarge

So far we were measuring tests time. In below table, the time it takes to start and stop Zalenium is also included as is relevant when using Zalenium in a CI. Docker pull time is around 1 minute and is not included in below stats.

These tests were run in Google Chrome and were run several times in our Jenkins.

The machine used for this 3rd environment is an AWS c3.4xlarge

| Environment |   # of Tests   |   Threads  |   Avg. Time VR** OFF  |
|:-----------:|:--------------:|:----------:|:---------------------:|
|      3      |        20      |      4     |       ~2min 10sec     |
|      3      |        30      |      6     |       ~2min 22sec     |
|      3      |        40      |      8     |       ~2min 40sec     |
|      3      |        50      |      10    |       ~3min 30sec     |

### General considerations
These come from our findings and input from this [comment](https://github.com/zalando/zalenium/issues/116#issuecomment-304790225)

* In general, more memory doesn't mean better performance. Increasing CPU cores brings better performance, we still need to find what 
the limit for CPU cores is.
* Having more CPU cores could potentially allow the creation of more containers concurrently, but quoting [@tacf](https://github.com/tacf) 
on his [comment](https://github.com/zalando/zalenium/issues/116#issuecomment-304790225) 
  > Another side note is that when testing heavy load on the same setup is easy to pass the point where you overload the machine with   
  containers and tests start failing because the grid doesn't respond in time.
* Also consider if your SUT is ready to receive the number of threads you are planning to execute.
* More importantly, double check that your tests are 100% independent from each other and that they can be executed in parallel without
any issues.

