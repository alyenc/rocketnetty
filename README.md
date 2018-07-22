# rocketnetty

netty transaction framework from rocketMQ

一个从RocketMQ源代码中抽取的Netty底层通信框架。

* 首先，服务端定义请求和响应处理类，必须继承NettyProcessor.
* 其次，数据传输通过RemotingMessage，所有传输的数据都要封装为RemotingMessage。
* 然后，分别实例化客户端和服务器，服务端通过调用registerProcessor方法注册请求处理器，所有请求交给请求处理器处理。服务端处理完成请求后，返回响应给客户端。

底层框架，待后期优化后，可以应用在远程调用中。
