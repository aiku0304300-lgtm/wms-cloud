package com.wms.check.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.check.entity.DemoSecondVerificationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 不需要自定义 SQL——查待重试记录、更新重试结果都是按主键/单字段等值条件，
 * BaseMapper 自带的 selectList/updateById 配 LambdaQueryWrapper 就够用。
 */
@Mapper
public interface DemoSecondVerificationLogMapper extends BaseMapper<DemoSecondVerificationLog> {
}
