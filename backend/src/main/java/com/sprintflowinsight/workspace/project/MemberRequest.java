package com.sprintflowinsight.workspace.project;

import java.util.*;
import org.springframework.web.bind.annotation.*;

public record MemberRequest(
    @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email String email,
    @jakarta.validation.constraints.NotBlank String role) {}
