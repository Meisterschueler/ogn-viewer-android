package com.meisterschueler.ognviewer.common;


import org.ogn.client.OgnClient;
import org.ogn.client.OgnClientFactory;
import org.ogn.commons.beacon.descriptor.AircraftDescriptorProvider;
import org.ogn.commons.db.FileDbDescriptorProvider;
import org.ogn.commons.db.ogn.OgnDb;

import java.util.Arrays;

public enum AircraftDescriptorProviderHelper {
    INSTANCE;

    private static final AircraftDescriptorProvider adp1 = new CustomAircraftDescriptorProvider();
    private static final AircraftDescriptorProvider adp2 = new FileDbDescriptorProvider<>(OgnDb.class);

    public static final AircraftDescriptorProvider getCustomDbAircraftDescriptorProvider() {
        return adp1;
    }

    public static final AircraftDescriptorProvider getOgnDbAircraftDescriptorProvider() {
        return adp2;
    }

    public static final OgnClient getOgnClient(String serverName) {
        //String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; //TODO: find a workaround 2018-08-26
        return OgnClientFactory.getBuilder().descriptorProviders(Arrays.asList(adp1, adp2))
                .serverName(serverName).appName("ogn-viewer").appVersion("1.4.5").build();
    }
}
