package com.opc.platform.tag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opc.platform.common.enums.ErrorCode;
import com.opc.platform.common.exception.BusinessException;
import com.opc.platform.tag.dto.TagCreateDTO;
import com.opc.platform.tag.dto.TagUpdateDTO;
import com.opc.platform.tag.entity.Tag;
import com.opc.platform.tag.mapper.TagMapper;
import com.opc.platform.tag.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagMapper tagMapper;

    public TagVO createTag(TagCreateDTO dto) {
        Tag tag = new Tag();
        copyCreateFields(dto, tag);
        ensureUnique(tag.getName(), tag.getTagType(), null);
        tagMapper.insert(tag);
        return toVO(tagMapper.selectById(tag.getId()));
    }

    public TagVO updateTag(Long id, TagUpdateDTO dto) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tag not found");
        }
        copyUpdateFields(dto, tag);
        ensureUnique(tag.getName(), tag.getTagType(), id);
        tagMapper.updateById(tag);
        return toVO(tagMapper.selectById(id));
    }

    @Transactional
    public void deleteTag(Long id) {
        Tag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tag not found");
        }
        if (tagMapper.countCaseOrPolicyReferences(id) > 0) {
            throw new BusinessException(ErrorCode.CONFLICT, "标签已被案例或政策引用，不能删除");
        }
        try {
            tagMapper.deleteById(id);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.CONFLICT, "标签仍有关联数据，不能删除");
        }
    }

    public List<TagVO> listTags(String tagType) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(StringUtils.hasText(tagType), Tag::getTagType, trim(tagType))
                .orderByAsc(Tag::getSortOrder)
                .orderByAsc(Tag::getId);

        return tagMapper.selectList(wrapper).stream()
                .map(this::toVO)
                .toList();
    }

    private void copyCreateFields(TagCreateDTO dto, Tag tag) {
        tag.setName(trim(dto.getName()));
        tag.setTagType(trim(dto.getTagType()));
        tag.setIsIndustry(Boolean.TRUE.equals(dto.getIsIndustry()));
        tag.setSortOrder(defaultSortOrder(dto.getSortOrder()));
    }

    private void copyUpdateFields(TagUpdateDTO dto, Tag tag) {
        tag.setName(trim(dto.getName()));
        tag.setTagType(trim(dto.getTagType()));
        tag.setIsIndustry(Boolean.TRUE.equals(dto.getIsIndustry()));
        tag.setSortOrder(defaultSortOrder(dto.getSortOrder()));
    }

    private void ensureUnique(String name, String tagType, Long currentId) {
        LambdaQueryWrapper<Tag> wrapper = new LambdaQueryWrapper<Tag>()
                .eq(Tag::getName, name)
                .eq(Tag::getTagType, tagType)
                .ne(currentId != null, Tag::getId, currentId);

        if (tagMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Tag already exists");
        }
    }

    private TagVO toVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setTagType(tag.getTagType());
        vo.setIsIndustry(Boolean.TRUE.equals(tag.getIsIndustry()));
        vo.setSortOrder(tag.getSortOrder());
        return vo;
    }

    private Integer defaultSortOrder(Integer sortOrder) {
        return sortOrder == null ? 0 : sortOrder;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
