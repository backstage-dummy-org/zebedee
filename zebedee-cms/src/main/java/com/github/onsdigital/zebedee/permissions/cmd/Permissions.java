package com.github.onsdigital.zebedee.permissions.cmd;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.github.onsdigital.zebedee.permissions.cmd.CRUD.CREATE;
import static com.github.onsdigital.zebedee.permissions.cmd.CRUD.DELETE;
import static com.github.onsdigital.zebedee.permissions.cmd.CRUD.READ;
import static com.github.onsdigital.zebedee.permissions.cmd.CRUD.UPDATE;


public class Permissions {

    public static Permissions permitCreateReadUpdateDelete() {
        return new Permissions().permit(CREATE, READ, UPDATE, DELETE);
    }

    public static Permissions permitRead() {
        return new Permissions().permit(READ);
    }

    public static Permissions permitNone() {
        return new Permissions();
    }

    public static Permissions premitNone() {
        return new Permissions();
    }

    private Set<CRUD> permissions;

    public Permissions() {
        this.permissions = new HashSet<>();
    }

    public Permissions(CRUD... grantedPermissions) {
        this.permissions = new LinkedHashSet<>();
        permit(grantedPermissions);
    }

    public Set<CRUD> getPermissions() {
        return permissions;
    }

    public Permissions setPermissions(Set<CRUD> permissions) {
        this.permissions = permissions;
        return this;
    }

    public Permissions grantPermission(CRUD permission) {
        this.permissions.add(permission);
        return this;
    }

    public Permissions permit(CRUD... permissions) {
        for (CRUD p : permissions) {
            this.permissions.add(p);
        }
        return this;
    }
}
