package com.wms.check.mapper;


import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.check.entity.DemoOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DemoOrderMapper extends BaseMapper<DemoOrder> {

    /**
     * 原子递增该订单的扫描序号计数器。
     * 必须和 selectLastInsertId() 跑在同一个数据库连接上，否则读回来的是别人的号——
     * 靠调用方的 @Transactional 把连接绑在当前线程来保证。
     * LAST_INSERT_ID(expr) 会把 expr 的值存进本连接的 session 变量，同时作为列值写入。
     *
     * @return 影响行数
     */
    @Update("UPDATE demo_order set last_scan_order = LAST_INSERT_ID(last_scan_order + 1) WHERE id = #{orderId}")
    int incrementScanOrder(@Param("orderId") Long orderId);

    /**
     * 读回上一步 LAST_INSERT_ID(expr) 存进当前连接 session 里的值，也就是刚分配到的序号。
     */
    @Select("SELECT LAST_INSERT_ID()")
    Integer selectLastInsertId();

}
