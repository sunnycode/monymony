/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.ycld.monymony;

import io.ycld.monymony.lib.utils.JsonUtils;
import io.ycld.redissolve.struct.map.RedisMap;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import com.google.common.collect.ImmutableMap;

@Path("/checks")
public class FancyCheckResource {
  private static final String GET_PASSWORD = System.getProperty("get.password"); // FIXME real
  private static final String POST_PASSWORD = System.getProperty("post.password"); // FIXME real

  private static final int MAX_TTL = 25 * 60 * 60;
  private static final int MIN_TTL = 10;

  private static final String KEY_REGEX = "^[a-zA-Z0-9:_\\-\\.]+$";

  private final RedisMap redis;

  private DateTimeFormatter dateFormat = ISODateTimeFormat.basicDateTime().withZone(
      DateTimeZone.UTC);

  @Inject
  public FancyCheckResource(RedisMap redis) {
    this.redis = redis;
  }

  @GET
  @Path("/{check}")
  public Response checkValue(final @PathParam("check") String checkName,
      final @QueryParam("maxage") Integer maxage, final @QueryParam("lhs") String lhs,
      final @QueryParam("op") String op, final @QueryParam("rhs") String rhs,
      final @QueryParam("pass") String pass) {
    if ((pass == null) || !(pass.equals(GET_PASSWORD) || pass.equals(POST_PASSWORD))) {
      return Response.status(Status.OK).entity("ERROR:not_authorized\n").build();
    }

    if (!validateKey(checkName)) {
      return Response.status(Status.OK).entity("ERROR:invalid_key\n").build();
    }

    byte[] value = redis.getLatest(checkName.getBytes());

    if (value == null) {
      return Response.status(Status.OK).entity("ERROR:not_found\n").build();
    }

    Map<String, Object> jsonVal = JsonUtils.fromJson(LinkedHashMap.class, value);

    if (maxage != null) {
      String ts = (String) jsonVal.get("ts");
      DateTime original = dateFormat.parseDateTime(ts);
      DateTime oldest = new DateTime().minusSeconds(maxage);

      if (original.isBefore(oldest)) {
        return Response.status(Status.OK).entity("ERROR:stale:" + ts + "\n").build();
      }
    }

    if (lhs == null) {
      return Response.status(Status.OK)
          .entity("OK:" + makeExpr(checkName, null, null, null) + "\n").build();
    }

    if (!jsonVal.containsKey(lhs)) {
      return Response.status(Status.OK)
          .entity("ERROR:not_found:" + makeExpr(checkName, lhs, null, null) + "\n").build();
    }

    Object lhsVal = jsonVal.get(lhs);

    if (lhsVal == null || lhsVal.equals("")) {
      return Response.status(Status.OK)
          .entity("ERROR:not_found:" + makeExpr(checkName, lhs, null, null) + "\n").build();
    }

    if (rhs == null) {
      return Response.status(Status.OK)
          .entity("OK:" + makeExpr(checkName, lhs, null, lhsVal.toString()) + "\n").build();
    }

    String theOp = op != null ? op : "eq";

    if (rhs.trim().equals("")) {
      return Response.status(Status.OK).entity("ERROR:invalid_rhs_value\n").build();
    }

    return evalExpr(checkName, lhs, rhs, lhsVal, theOp);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Path("/{check}")
  public Response setValue(final @PathParam("check") String checkName,
      final @QueryParam("ttl") Integer ttl, final @QueryParam("pass") String pass, byte[] data) {
    if (pass == null || !pass.equals(POST_PASSWORD)) {
      return Response.status(Status.OK).entity("ERROR:not_authorized\n").build();
    }

    if (!validateKey(checkName)) {
      return Response.status(Status.OK).entity("ERROR:invalid_key\n").build();
    }

    Integer theTtl = ttl;

    if (theTtl == null) {
      theTtl = MAX_TTL;
    }

    theTtl = Math.max(MIN_TTL, theTtl);
    theTtl = Math.min(theTtl, MAX_TTL);

    try {
      JsonUtils.fromJson(LinkedHashMap.class, data);
    } catch (Exception e) {
      return Response.status(Status.BAD_REQUEST).entity("ERROR:invalid_json\n").build();
    }

    redis.setAll(checkName.getBytes(), data, theTtl);

    return Response.status(Status.OK).entity("OK\n").build();
  }

  private static boolean validateKey(String key) {
    return key.matches(KEY_REGEX);
  }

  private Response evalExpr(final String check, final String lhs, final String rhs, Object lhsVal,
      String theOp) {
    if (lhsVal instanceof Number) {
      long lhsLongValue = ((Number) lhsVal).longValue();
      Long rhsVal = Long.parseLong(rhs);
      boolean isOk = false;

      switch (theOp.toLowerCase()) {
        case "eq":
          isOk = lhsLongValue == rhsVal;
          break;
        case "ne":
          isOk = lhsLongValue != rhsVal;
          break;
        case "le":
          isOk = lhsLongValue <= rhsVal;
          break;
        case "lt":
          isOk = lhsLongValue < rhsVal;
          break;
        case "ge":
          isOk = lhsLongValue >= rhsVal;
          break;
        case "gt":
          isOk = lhsLongValue > rhsVal;
          break;
        default:
          return Response.status(Status.OK).entity("ERROR:invalid_op:" + theOp + "\n").build();
      }

      if (isOk) {
        return Response.status(Status.OK).entity("OK:" + makeExpr(check, lhs, theOp, rhs) + "\n")
            .build();
      } else {
        return Response.status(Status.OK)
            .entity("ERROR:" + makeExpr(check, lhs, "!" + theOp, rhs) + "\n").build();
      }
    } else {
      int lhsComparison = lhsVal.toString().compareTo(rhs);
      boolean isOk = false;

      switch (theOp.toLowerCase()) {
        case "eq":
          isOk = lhsComparison == 0;
          break;
        case "ne":
          isOk = lhsComparison != 0;
          break;
        case "le":
          isOk = lhsComparison <= 0;
          break;
        case "lt":
          isOk = lhsComparison < 0;
          break;
        case "ge":
          isOk = lhsComparison >= 0;
          break;
        case "gt":
          isOk = lhsComparison > 0;
          break;
        default:
          return Response.status(Status.OK).entity("ERROR:invalid_op:" + theOp + "\n").build();
      }

      if (isOk) {
        return Response.status(Status.OK).entity("OK:" + makeExpr(check, lhs, theOp, rhs) + "\n")
            .build();
      } else {
        return Response.status(Status.OK)
            .entity("ERROR:" + makeExpr(check, lhs, "!" + theOp, rhs) + "\n").build();
      }
    }
  }

  public static String makeExpr(String check, String lhs, String op, String rhs) {
    ImmutableMap.Builder<String, Object> result = ImmutableMap.builder();
    result.put("c", check);

    if (lhs != null) {
      result.put("lhs", lhs);
    }

    if (op != null) {
      result.put("op", op);
    }

    if (rhs != null) {
      result.put("rhs", rhs);
    }

    return JsonUtils.asJson(result.build());
  }
}
