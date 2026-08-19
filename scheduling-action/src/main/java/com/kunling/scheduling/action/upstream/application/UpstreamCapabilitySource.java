package com.kunling.scheduling.action.upstream.application;

import java.util.List;

public interface UpstreamCapabilitySource {

    List<AtomicCapabilityDescriptor> fetchCapabilities();
}
