#!/bin/bash

# -- > VACO internal processing SQS queue
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs')
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs-validation')
echo $(awslocal sqs create-queue --queue-name 'vaco-jobs-conversion')
echo $(awslocal sqs create-queue --queue-name 'vaco-errors')
echo $(awslocal sqs create-queue --queue-name 'DLQ-rules-processing')
# -- > External rules result ingesting
echo $(awslocal sqs create-queue --queue-name 'rules-results')
# -- > each rule needs to have a matching queue
RULE_NAMES=('rules-processing-gtfs-canonical' 'rules-processing-netex-entur' 'rules-processing-netex2gtfs-entur' 'rules-processing-gtfs2netex-fintraffic' 'rules-processing-gbfs-entur' )

for rule in "${RULE_NAMES[@]}"
do
  echo "Creating rule $rule queues"
  awslocal sqs create-queue --queue-name "$rule"
  awslocal sqs set-queue-attributes \
      --queue-url http://sqs.eu-north-1.localhost.localstack.cloud:4566/000000000000/$rule \
      --attributes '{
          "RedrivePolicy": "{\"deadLetterTargetArn\":\"arn:aws:sqs:eu-north-1:000000000000:DLQ-rules-processing\",\"maxReceiveCount\":\"1\"}"
      }'
done

# --> List SQS Queues
echo Listing queues ...
echo $(awslocal sqs list-queues)

# -- > Create S3 buckets used by VACO
echo $(awslocal s3api create-bucket --bucket 'digitraffic-tis-processing-local' --create-bucket-configuration LocationConstraint=eu-north-1)

# --> List S3 buckets
echo $(awslocal s3api list-buckets)

# --> Verify SES fake email identity
echo $(awslocal ses verify-email-identity --email-address 'no-reply@mail.localhost')

KEY_ID=$(awslocal kms create-key --description "credentials kms key" --key-usage ENCRYPT_DECRYPT --query 'KeyMetadata.KeyId' --output text)

awslocal kms create-alias --alias-name alias/vaco_credentials_db_key --target-key-id $KEY_ID




