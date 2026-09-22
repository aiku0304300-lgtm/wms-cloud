package com.wms.check.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wms.check.entity.DemoBox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DemoBoxMapper extends BaseMapper<DemoBox> {

    /**
     * CAS抢占箱子：id匹配且status=0空闲，才更新为占用
     * 返回影响行数：1成功 / 0失败
     */
    @Update("UPDATE demo_box SET status = 1, update_time = NOW() " +
            "WHERE id = #{boxId} AND status = 0")
    int tryLockBox(@Param("boxId") Long boxId);

    /**
     * CAS 成功之后，把分配到的序号回填到这一行。
     * 生产也是分两条 UPDATE（先抢占、再回填），这里保持一致。
     * 不用再判 status——执行到这里箱子已被上面的 CAS 锁住，没有竞争了。
     *
     * @return 影响行数
     */
    @Update("UPDATE demo_box SET status = 1,update_time= NOW(),scan_order=#{scanOrder} WHERE id = #{boxId}")
    int updateScanOrder(@Param("boxId") Long boxId, @Param("scanOrder") Integer scanOrder);

}
