package com.opc.platform.dashboard.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class RecentUpdateVO {

    private String itemType;

    private Long itemId;

    private String title;

    private Long regionId;

    private String regionName;

    private LocalDate updatedDate;

    private String status;
}
