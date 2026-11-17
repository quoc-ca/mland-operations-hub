package edu.fpt.sep490_g22.ppswbs_backend.features.readyringsales.service;

import jakarta.servlet.http.HttpServletRequest;

public interface RetailActorResolver {

    RetailMemberActor requireActiveMember(HttpServletRequest request);
}
