# A note on Zalenium's perfomance

As Zalenium gained more users over time, it got used in more diverse ways and not only as a small disposable grid to run a few tests.
These new scenarios showed us some opportunities to improve Zalenium's performance. Thanks to our users' feedback from our users, 
and also from issue [#116](https://github.com/zalando/zalenium/issues/116), we decided to work on this topic and to give some 
tips/guidelines on its usage.

The following data is the result of running a simple test many times in different combinations of threads and hardware specifications. 
A simple test is one that just opens the browser, goes to any given url (e.g. www.google.com), and closes the browser. **The purpose 
of this data is to serve as a guide when you want to setup your tests using Zalenium, these values are not written on stone and might
differ in your environment. Again, please take this just as a guide.** 

*This is a live document and PRs are welcome to add data based on your findings in your environment.*

### Execution times

| Environment |   # of Tests   |   Threads  |   Avg. Time VR** ON   |   Avg. Time VR** OFF   |
|:-----------:|:--------------:|:----------:|:---------------------:|:----------------------:|
|      1      |        50      |      4     |       ~8min 30sec     |        ~5min 15sec     |
|      1      |        50      |      6     |       ~7min 15sec     |        ~4min 30sec     |
|      1      |        50      |      8     |           X*          |        ~4min 15sec     |
|      1      |        50      |      10    |           X*          |        ~4min 45sec     |
|      1      |        50      |      12    |           X*          |           X*           |
|      2      |        30      |      6     |         ~3min         |                        |
|      2      |        35      |      8     |         ~4min         |                        |
|      2      |        50      |      10    |         ~5min         |          ~4min         |
|      2      |        60      |      12    |         ~6min         |          ~8min         |


*X=The containers were not stable enough in order to avoid affecting the tests.

**VR=Video recording

### Environments

| ID |        Hardware          |    Processor    |        RAM         |           OS         |                    Docker               |
|:--:|:------------------------:|:---------------:|:------------------:|:--------------------:|:---------------------------------------:|
| 1  | MacBook Pro (Early 2015) | 2,7GHz 2vCPU i5 | 16 GB 1867MHz DDR3 | macOS Sierra 10.12.5 | Version 17.03.1-ce-mac12 (4 CPUs, 16GB) |
| 1  | Asus                     | 8vCPU i7        | 16 GB DDR3         | Ubuntu               | Version ...                             |
