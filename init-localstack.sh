#!/bin/bash

# -- > Create SQS queues used by VACO
echo $(awslocal sqs create-queue --queue-name 'vaco_jobs')
echo $(awslocal sqs create-queue --queue-name 'vaco_jobs_validation')
echo $(awslocal sqs create-queue --queue-name 'vaco_jobs_conversion')

# --> List SQS Queues
echo Listing queues ...
echo $(awslocal sqs list-queues)
