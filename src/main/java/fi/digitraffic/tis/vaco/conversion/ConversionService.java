package fi.digitraffic.tis.vaco.conversion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ConversionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversionService.class);

    public void convert() {
        // TODO: conversion process goes here :)
        LOGGER.info("Convert...");
    }
}
