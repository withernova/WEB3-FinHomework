package com.maka.service.impl;

import com.maka.mapper.DataViewMapper;
import com.maka.service.DataViewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DataViewServiceImpl implements DataViewService {

    @Autowired
    DataViewMapper dataViewMapper;
    @Override
    public int getRescuedCount()
    {
        return dataViewMapper.selectRescuedTask().size();
    }
    @Override
    public int getRescuingCount()
    {
        return dataViewMapper.selectRescuingTask().size();
    }
    @Override
    public int getTotalPeopleNum()
    {
        return dataViewMapper.selectAllRescuer().size();
    }
}
