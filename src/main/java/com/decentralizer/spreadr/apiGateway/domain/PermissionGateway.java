package com.decentralizer.spreadr.apiGateway.domain;

import com.decentralizer.spreadr.modules.appconfig.postgres.entities.ControllerEntity;
import com.decentralizer.spreadr.modules.appconfig.postgres.entities.UserEntity;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
public class PermissionGateway {
    private long id;
    private ZonedDateTime fromDate;
    private ZonedDateTime toDate;
    private boolean active;
    private UserEntity createdBy;
    private UserEntity permissionFor;
    private Set<ControllerEntity> controllers = new HashSet<>();
    private UUID version;
}
