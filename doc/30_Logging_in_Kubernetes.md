# Logging in Kubernetes

The document explains ways to monitor logs from multiple related Pods.

## Show logs via "kubectl logs"

The simplest way is to use "kubectl logs". To get logs from all Pods having same label, we re-deploy the sender and receiver with new labels.

Delete current deployment

```console
sudo kubectl delete deploy/rabbitmq-sender
sudo kubectl delete deploy/rabbitmq-receiver
```

deployment/rabbitmq-app.yml change sender and receiver with same label (app=rabbitmq)

```diff
--- a/deployment/rabbitmq-app.yml
+++ b/deployment/rabbitmq-app.yml
@@ -5,12 +5,14 @@ metadata:
 spec:
   selector:
     matchLabels:
-      app: rabbitmq-sender
+      app: rabbitmq
+      mode: sender
   replicas: 1
   template:
     metadata:
       labels:
-        app: rabbitmq-sender
+        app: rabbitmq
+        mode: sender
     spec:
       containers:
       - name: rabbitmq-sender
@@ -32,12 +34,14 @@ metadata:
 spec:
   selector:
     matchLabels:
-      app: rabbitmq-receiver
+      app: rabbitmq
+      mode: receiver
   replicas: 2
   template:
     metadata:
       labels:
-        app: rabbitmq-receiver
+        app: rabbitmq
+        mode: receiver
     spec:
       containers:
       - name: rabbitmq-receiver
```

Collect all Pods having label "app=rabbitmq" and sort to show logs by time. Because the sender/receiver application logs Pod name, it is easy to see

- After sender sends a message, one receiver receives
- If one receiver rejects message with exception, another receiver receives it
- RabbitMQ evenly distributes messages among receivers

```console
sudo kubectl logs -l app=rabbitmq | sort
2018-04-23 08:02:52.533  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello.1'
2018-04-23 08:02:52.648  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [           main] o.s.c.support.DefaultLifecycleProcessor  : Starting beans in phase 2147483647
2018-04-23 08:02:52.699  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] o.s.a.r.c.CachingConnectionFactory       : Attempting to connect to: [rabbitmq-service:5672]
2018-04-23 08:02:52.774  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.1'
2018-04-23 08:02:53.337  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] o.s.a.r.c.CachingConnectionFactory       : Created new connection: rabbitConnectionFactory#6328d34a:0/SimpleConnection@2406892e [delegate=amqp://guest@10.102.234.100:5672/, localPort= 50050]
2018-04-23 08:02:53.535  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [           main] c.e.rabbitmqapp.RabbitMQApplication      : Started RabbitMQApplication in 11.706 seconds (JVM running for 13.59)
2018-04-23 08:02:53.536  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello..2'
2018-04-23 08:02:53.642  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..2'
2018-04-23 08:02:54.539  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello...3'
2018-04-23 08:02:54.686  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...3'
2018-04-23 08:02:55.541  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello.4'
2018-04-23 08:02:55.643 ERROR rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello.4
2018-04-23 08:02:55.749  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.4'
2018-04-23 08:02:56.544  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello..5'
2018-04-23 08:02:56.648  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..5'
2018-04-23 08:02:57.547  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello...6'
2018-04-23 08:02:57.655 ERROR rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello...6
2018-04-23 08:02:57.766  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...6'
```

## Show logs via "kubetail"

[kubetail](https://github.com/johanhaleby/kubetail) is a script under Apache license.

- It prints pod names in output. With that, we need not print host/docker name in spring log.
- As "tail -f", we can monitor the ongoing logs.
- It prints related Pods at the beginning. Not sure whether it works if we increase or decrease Pod replicas.
- It prints different pods' logs in different color

```console
$ wget https://raw.githubusercontent.com/johanhaleby/kubetail/master/kubetail
$ chmod a+x kubetail
$ sudo ./kubetail -b -l app=rabbitmq
Will tail 3 logs...
rabbitmq-receiver-c9d49487f-9ztmp
rabbitmq-receiver-c9d49487f-qbfr9
rabbitmq-sender-7656699c75-tdrm7
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:43.196  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello...1788'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:44.197  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello.1789'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:45.211  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello..1790'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:46.213  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello...1791'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:47.215  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello.1792'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:48.219  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello..1793'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:49.219  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello...1794'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:50.220  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello.1795'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:51.222  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello..1796'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:52.224  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello...1797'
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:44.301  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.1789'
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:46.316  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...1791'
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:48.320 ERROR rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello..1793
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:49.322  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...1794'
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:51.409  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..1796'
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:52.427  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...1797'
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:43.298  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...1788'
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:45.314  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..1790'
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:47.323  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.1792'
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:48.422  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..1793'
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:50.322  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.1795'
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:52.325 ERROR rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello...1797
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:53.225  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello.1798'
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:53.326 ERROR rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello.1798
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:53.430 ERROR rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello.1798
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:53.533  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello.1798'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:54.226  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello..1799'
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:54.328 ERROR rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello..1799
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:54.430 ERROR rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received Exception Hello..1799
[rabbitmq-receiver-c9d49487f-qbfr9] 2018-04-23 08:32:54.532  INFO rabbitmq-receiver-c9d49487f-qbfr9 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello..1799'
[rabbitmq-sender-7656699c75-tdrm7] 2018-04-23 08:32:55.227  INFO rabbitmq-sender-7656699c75-tdrm7 1 --- [pool-4-thread-1] com.example.rabbitmqapp.MySender         : [x] Sent 'Hello...1800'
[rabbitmq-receiver-c9d49487f-9ztmp] 2018-04-23 08:32:55.330  INFO rabbitmq-receiver-c9d49487f-9ztmp 1 --- [cTaskExecutor-1] com.example.rabbitmqapp.MyReceiver       : [x] Received 'Hello...1800'
```

## TODO

### Logspout

### Fluentd

https://github.com/kubernetes/minikube/issues/339
https://github.com/kubernetes/kubernetes/tree/master/cluster/addons/fluentd-elasticsearch
https://dzone.com/articles/kubernetes-log-analysis-with-fluentd-elasticsearch
https://tonybai.com/2017/03/03/implement-kubernetes-cluster-level-logging-with-fluentd-and-elasticsearch-stack/
http://www.cnblogs.com/ericnie/p/6897348.html
https://jimmysong.io/posts/kubernetes-fluentd-elasticsearch-installation/

https://www.elastic.co/guide/en/elasticsearch/reference/6.2/docker.html
https://www.elastic.co/guide/en/beats/filebeat/6.2/running-on-docker.html
https://www.elastic.co/guide/en/beats/filebeat/6.2/running-on-kubernetes.html
https://www.elastic.co/guide/en/kibana/6.2/docker.html

```console
vagrant@k8smaster:~/fluentd/kubernetes-1.10.0/cluster/addons/fluentd-elasticsearch$ sudo kubectl label node/k8sworker1 beta.kubernetes.io/fluentd-ds-ready=true
node "k8sworker1" labeled
vagrant@k8smaster:~/fluentd/kubernetes-1.10.0/cluster/addons/fluentd-elasticsearch$ sudo kubectl describe nodes/k8sworker1 | head
Name:               k8sworker1
Roles:              <none>
Labels:             beta.kubernetes.io/arch=amd64
                    beta.kubernetes.io/fluentd-ds-ready=true
                    beta.kubernetes.io/os=linux
                    kubernetes.io/hostname=k8sworker1
Annotations:        node.alpha.kubernetes.io/ttl=0
                    volumes.kubernetes.io/controller-managed-attach-detach=true
CreationTimestamp:  Wed, 11 Apr 2018 03:35:50 +0000

vagrant@k8smaster:~/fluentd/kubernetes-1.10.0/cluster/addons/fluentd-elasticsearch$ sudo kubectl describe --namespace=kube-system po/fluentd-es-v2.0.4-fd9dz
Name:           fluentd-es-v2.0.4-fd9dz
Namespace:      kube-system
Node:           k8sworker1/192.168.8.11
Start Time:     Wed, 18 Apr 2018 07:10:52 +0000
```

https://raw.githubusercontent.com/kubernetes/kubernetes/v1.10.0/cluster/addons/fluentd-elasticsearch/es-service.yaml
https://raw.githubusercontent.com/kubernetes/kubernetes/v1.10.0/cluster/addons/fluentd-elasticsearch/es-statefulset.yaml
https://raw.githubusercontent.com/kubernetes/kubernetes/v1.10.0/cluster/addons/fluentd-elasticsearch/fluentd-es-configmap.yaml
https://raw.githubusercontent.com/kubernetes/kubernetes/v1.10.0/cluster/addons/fluentd-elasticsearch/fluentd-es-ds.yaml
https://raw.githubusercontent.com/kubernetes/kubernetes/v1.10.0/cluster/addons/fluentd-elasticsearch/kibana-deployment.yaml
https://raw.githubusercontent.com/kubernetes/kubernetes/v1.10.0/cluster/addons/fluentd-elasticsearch/kibana-service.yaml

```console
kubectl run -i --tty alpine --image alpine:3.7
apk update
apk add curl

kubectl attach alpine-56bf549d58-4kgtq -c alpine -i -t

/ # curl http://elasticsearch-logging.kube-system.svc.cluster.local:9200/_cat/indices
yellow open filebeat-6.2.4-2018.04.20   7P6Ic136QxGmZwSBhtLw3g 5 1    61   0 91.1kb 91.1kb
yellow open logstash-2018.04.23         zCdYIYvXTC2oKWHLBoF7bA 5 1 57832   0 34.4mb 34.4mb
yellow open logstash-2018.04.20         vcKp2lZ8So66gkXrlQccWg 5 1 22761   0 10.7mb 10.7mb
yellow open filebeat-6.2.4-2018.04.23   RbuLnvGbRES5nD4HJOwnEw 5 1 58574   0   13mb   13mb
green  open .monitoring-es-6-2018.04.23 hJQeA4EETjaYGn24ez-aRQ 1 0 19096 246  8.7mb  8.7mb
```