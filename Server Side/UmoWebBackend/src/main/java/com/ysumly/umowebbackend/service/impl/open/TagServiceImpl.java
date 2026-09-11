package com.ysumly.umowebbackend.service.impl.open;

import com.ysumly.umowebbackend.mapper.TagMapper;
import com.ysumly.umowebbackend.model.entity.Tag;
import com.ysumly.umowebbackend.model.vo.TagVO;
import com.ysumly.umowebbackend.service.open.TagService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;

    public TagServiceImpl(TagMapper tagMapper) {
        this.tagMapper = tagMapper;
    }

    @Override
    public List<TagVO> getAll() {
        return tagMapper.findAll().stream().map(t -> {
            TagVO vo = new TagVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setSlug(t.getSlug());
            return vo;
        }).toList();
    }
}
