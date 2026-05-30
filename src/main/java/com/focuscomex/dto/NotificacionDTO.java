package com.focuscomex.dto;

import java.util.Date;

import com.focuscomex.enums.ActionNotification;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificacionDTO {

    private Long id;
    private Date createdAt;
    private Date updatedAt;
    private boolean read;
    private ActionNotification action;

}