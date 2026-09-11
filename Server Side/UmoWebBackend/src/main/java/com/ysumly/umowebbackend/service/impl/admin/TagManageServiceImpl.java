package com.ysumly.umowebbackend.service.impl.admin;

import com.ysumly.umowebbackend.common.exception.BusinessException;
import com.ysumly.umowebbackend.common.exception.NotFoundException;
import com.ysumly.umowebbackend.mapper.ContentTagMapper;
import com.ysumly.umowebbackend.mapper.TagMapper;
import com.ysumly.umowebbackend.model.dto.TagSaveRequest;
import com.ysumly.umowebbackend.model.entity.Tag;
import com.ysumly.umowebbackend.model.vo.TagVO;
import com.ysumly.umowebbackend.service.admin.TagManageService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagManageServiceImpl implements TagManageService {

    private final TagMapper tagMapper;
    private final ContentTagMapper contentTagMapper;

    public TagManageServiceImpl(TagMapper tagMapper,
                                ContentTagMapper contentTagMapper) {
        this.tagMapper = tagMapper;
        this.contentTagMapper = contentTagMapper;
    }

    @Override
    public List<TagVO> getAll() {
        return tagMapper.findAll().stream().map(this::toVO).toList();
    }

    @Override
    public TagVO create(TagSaveRequest request) {
        Tag tag = new Tag();
        tag.setName(request.getName());
        tag.setSlug(request.getSlug());
        tagMapper.insert(tag);
        return toVO(tag);
    }

    @Override
    public TagVO update(Long id, TagSaveRequest request) {
        Tag tag = tagMapper.findById(id);
        if (tag == null) {
            throw new NotFoundException("Tag not found: id=" + id);
        }
        tag.setName(request.getName());
        tag.setSlug(request.getSlug());
        tagMapper.update(tag);
        return toVO(tag);
    }

    @Override
    public void delete(Long id) {
        if (tagMapper.findById(id) == null) {
            throw new NotFoundException("Tag not found: id=" + id);
        }
        long contentCount = contentTagMapper.countContentsByTagId(id);
        if (contentCount > 0) {
            throw new BusinessException(409,
                    "Cannot delete tag: it is associated with " + contentCount + " content(s)");
        }
        tagMapper.delete(id);
    }

    private TagVO toVO(Tag tag) {
        TagVO vo = new TagVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        return vo;
    }
}
