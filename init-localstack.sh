#!/bin/bash

# -- > Create SQS queues used by VACO
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs')
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs-validation')
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs-conversion')
echo $(awslocal sqs create-queue --queue-name 'vaco-errors')
# -- > each rule needs to have a matching queue
echo $(awslocal sqs create-queue --queue-name 'vaco-rules-gtfs-canonical-v4_0_0')
echo $(awslocal sqs create-queue --queue-name 'vaco-rules-gtfs-canonical-v4_1_0')
echo $(awslocal sqs create-queue --queue-name 'vaco-rules-netex-entur-v1_0_1')

# --> List SQS Queues
echo Listing queues ...
echo $(awslocal sqs list-queues)

# -- > Create S3 buckets used by VACO
echo $(awslocal s3api create-bucket --bucket 'vaco-local-processing' --create-bucket-configuration LocationConstraint=eu-north-1)

# --> List S3 buckets
echo $(awslocal s3api list-buckets)
