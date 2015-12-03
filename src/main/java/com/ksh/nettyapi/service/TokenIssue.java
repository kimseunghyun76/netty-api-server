package com.ksh.nettyapi.service;

import com.google.gson.JsonObject;
import com.ksh.nettyapi.core.ApiRequestTemplate;
import com.ksh.nettyapi.core.JedisHelper;
import com.ksh.nettyapi.core.KeyMaker;
import com.ksh.nettyapi.exception.RequestParamException;
import com.ksh.nettyapi.exception.ServiceException;
import com.ksh.nettyapi.service.dao.TokenKey;
import org.apache.ibatis.session.SqlSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import redis.clients.jedis.Jedis;

import java.util.Map;

/**
 * Created by Helloworld
 * User : USER
 * Date : 2015-12-02
 * Time : 오후 7:00
 * To change this template use File | Settings | File and Code Templates.
 */@Service("tokenIssue")
   @Scope("prototype")
   public class TokenIssue extends ApiRequestTemplate {
    private static final JedisHelper helper = JedisHelper.getInstance();

    @Autowired
    private SqlSession sqlSession;

    public TokenIssue(Map<String, String> reqData) {
        super(reqData);
    }

    public void requestParamValidation() throws RequestParamException {
        if (StringUtils.isEmpty(this.reqData.get("userNo"))) {
            throw new RequestParamException("userNo이 없습니다.");
        }

        if (StringUtils.isEmpty(this.reqData.get("password"))) {
            throw new RequestParamException("password가 없습니다.");
        }
    }

    public void service() throws ServiceException {
        Jedis jedis = null;
        try {
            Map<String, Object> result = sqlSession.selectOne("users.userInfoByPassword", this.reqData);

            if (result != null) {
                final long threeHour = 60 * 60 * 3;
                long issueDate = System.currentTimeMillis() / 1000;
                String email = String.valueOf(result.get("USERID"));

                // token 만들기.
                JsonObject token = new JsonObject();
                token.addProperty("issueDate", issueDate);
                token.addProperty("expireDate", issueDate + threeHour);
                token.addProperty("email", email);
                token.addProperty("userNo", reqData.get("userNo"));

                // token 저장.
                KeyMaker tokenKey = new TokenKey(email, issueDate);
                jedis = helper.getConnection();
                jedis.setex(tokenKey.getKey(), 60 * 60 * 3, token.toString());

                // helper.
                this.apiResult.addProperty("resultCode", "200");
                this.apiResult.addProperty("message", "Success");
                this.apiResult.addProperty("token", tokenKey.getKey());
            }
            else {
                // 데이터 없음.
                this.apiResult.addProperty("resultCode", "404");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            this.apiResult.addProperty("resultCode", "500");
            helper.returnResource(jedis);
        }
    }
}