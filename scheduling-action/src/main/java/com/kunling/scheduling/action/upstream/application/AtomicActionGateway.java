package com.kunling.scheduling.action.upstream.application;

public interface AtomicActionGateway {

    AtomicActionResult execute(AtomicActionRequest request) throws InterruptedException;
}
