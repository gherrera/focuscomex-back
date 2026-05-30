package com.focuscomex.mapper;

public interface Mapper<DTO, E> {
    DTO mapToDTO(E entity);
}