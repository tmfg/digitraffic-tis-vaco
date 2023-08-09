#!/bin/bash

# -- > Create SQS queues used by VACO
echo $(awslocal sqs create-queue --queue-name 'vaco_jobs')
echo $(awslocal sqs create-queue --queue-name 'vaco_jobs_validation')
echo $(awslocal sqs create-queue --queue-name 'vaco_jobs_conversion')

# --> List SQS Queues
echo Listing queues ...
echo $(awslocal sqs list-queues)

# -- > Create S3 buckets used by VACO
echo $(awslocal s3api create-bucket --bucket 'vaco-local-processing' --create-bucket-configuration LocationConstraint=eu-central-1)

# --> List S3 buckets
echo $(awslocal s3api list-buckets)
