#!/bin/bash

# -- > VACO internal processing SQS queue
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs')
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs-validation')
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs-conversion')
echo $(awslocal sqs create-queue --queue-name 'vaco-errors')
# -- > External rules result ingesting
echo $(awslocal sqs create-queue --queue-name 'rules-results')
# -- > each rule needs to have a matching queue
echo $(awslocal sqs create-queue --queue-name 'rules-processing-gtfs-canonical')
echo $(awslocal sqs create-queue --queue-name 'rules-processing-netex-entur')
echo $(awslocal sqs create-queue --queue-name 'rules-processing-netex2gtfs-entur')
echo $(awslocal sqs create-queue --queue-name 'rules-processing-gtfs2netex-fintraffic')
echo $(awslocal sqs create-queue --queue-name 'rules-processing-gbfs-entur')

# --> List SQS Queues
echo Listing queues ...
echo $(awslocal sqs list-queues)

# -- > Create S3 buckets used by VACO
echo $(awslocal s3api create-bucket --bucket 'digitraffic-tis-processing-local' --create-bucket-configuration LocationConstraint=eu-north-1)

# --> List S3 buckets
echo $(awslocal s3api list-buckets)

# --> Verify SES fake email identity
echo $(awslocal ses verify-email-identity --email-address 'no-reply@mail.localhost')
