package com.loserico.networking.utils;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import static com.loserico.networking.constants.NetworkConstant.HTTP_HEADER_HTTP_CLIENT_IP;
import static com.loserico.networking.constants.NetworkConstant.HTTP_HEADER_HTTP_X_FORWARDED_FOR;
import static com.loserico.networking.constants.NetworkConstant.HTTP_HEADER_PROXY_CLIENT_IP;
import static com.loserico.networking.constants.NetworkConstant.HTTP_HEADER_WL_PROXY_CLIENT_IP;
import static com.loserico.networking.constants.NetworkConstant.HTTP_HEADER_X_FORWARDED_FOR;
import static com.loserico.networking.constants.NetworkConstant.HTTP_HEADER_X_REAL_IP;
import static com.loserico.networking.constants.NetworkConstant.HTTP_HEADER_X_REQUESTED_WITH;
import static com.loserico.networking.constants.NetworkConstant.UNKNOWN;

/**
 * 网络相关操作帮助类
 * <p>
 * Copyright: (C), 2019/12/25 10:49
 * <p>
 * <p>
 * Company: Sexy Uncle Inc.
 *
 * @author Rico Yu ricoyu520@gmail.com
 * @version 1.0
 */
@Slf4j
public final class HttpUtils {
	
	/**
	 * 获取客户端的IP
	 *
	 * @param request
	 * @return String
	 */
	public static String getRemortIP(HttpServletRequest request) {
		String xForwardedFor = request.getHeader(HTTP_HEADER_X_FORWARDED_FOR);
		if (xForwardedFor == null) {
			return request.getRemoteAddr();
		}
		return xForwardedFor;
	}
	
	/**
	 * 获取用户真实IP地址, 不使用request.getRemoteAddr()的原因是有可能用户使用了代理软件方式避免真实IP地址.
	 * 可是, 如果通过了多级反向代理的话, X-Forwarded-For的值并不止一个, 而是一串IP值
	 *
	 * @param request
	 * @return
	 */
	public static String getRemoteRealIP(HttpServletRequest request) {
		
		String ip = request.getHeader(HTTP_HEADER_X_FORWARDED_FOR);
		String ipDesc = "";
		
		if (ip != null && ip.length() != 0 && !UNKNOWN.equalsIgnoreCase(ip)) {
			// 多次反向代理后会有多个ip值, 第一个ip才是真实ip
			if (ip.indexOf(",") != -1) {
				ip = ip.split(",")[0];
				ipDesc = HTTP_HEADER_X_FORWARDED_FOR;
			}
		}
		if (isIpUndetermined(ip)) {
			ip = request.getHeader(HTTP_HEADER_PROXY_CLIENT_IP);
			ipDesc = HTTP_HEADER_PROXY_CLIENT_IP;
		}
		if (isIpUndetermined(ip)) {
			ip = request.getHeader(HTTP_HEADER_WL_PROXY_CLIENT_IP);
			ipDesc = HTTP_HEADER_WL_PROXY_CLIENT_IP;
		}
		if (isIpUndetermined(ip)) {
			ip = request.getHeader(HTTP_HEADER_HTTP_CLIENT_IP);
			ipDesc = HTTP_HEADER_HTTP_CLIENT_IP;
		}
		if (isIpUndetermined(ip)) {
			ip = request.getHeader(HTTP_HEADER_HTTP_X_FORWARDED_FOR);
			ipDesc = HTTP_HEADER_HTTP_X_FORWARDED_FOR;
		}
		if (isIpUndetermined(ip)) {
			ip = request.getHeader(HTTP_HEADER_X_REAL_IP);
			ipDesc = HTTP_HEADER_X_REAL_IP;
		}
		if (isIpUndetermined(ip)) {
			ip = request.getRemoteAddr();
			ipDesc = "RemoteAddr";
		}
		log.debug("获取请求真实IP，IP来源: " + ipDesc);
		return ip;
	}
	
	/**
	 * 取X-Requested-With请求头, 判断值是否为XMLHttpRequest, 是的话认为是AJAX请求
	 *
	 * @param request
	 * @return boolean
	 */
	public static boolean isAjaxRequest(HttpServletRequest request) {
		String xRequestedWithHeader = request.getHeader(HTTP_HEADER_X_REQUESTED_WITH);
		if (isBlank(xRequestedWithHeader)) {
			return false;
		}
		return xRequestedWithHeader.equals("XMLHttpRequest");
	}
	
	/**
	 * 将所有的请求头放到Map里面返回
	 *
	 * @param request
	 * @return Map<String, Object>
	 */
	public static Map<String, Object> getRequestHeadInfo(HttpServletRequest request) {
		
		Map<String, Object> map = new HashMap<String, Object>();
		
		Enumeration headerNames = request.getHeaderNames();
		while (headerNames.hasMoreElements()) {
			String key = (String) headerNames.nextElement();
			String value = request.getHeader(key);
			map.put(key, value);
		}
		
		return map;
	}
	
	/**
	 * 获取所有的cookie值, 以Map形式返回
	 *
	 * @param request
	 * @return Map<String, Object>
	 */
	public static Map<String, Object> getRequestCookies(HttpServletRequest request) {
		Map<String, Object> result = new HashMap<String, Object>();
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				result.put(cookie.getName(), cookie.getValue());
			}
		}
		return result;
	}
	
	public static void removeRootCookies(HttpServletRequest request, HttpServletResponse response, String key) {
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			for (Cookie cookie : cookies) {
				if (cookie.getName().equals(key)) {
					cookie.setValue(null);
					cookie.setMaxAge(0);
					cookie.setPath("/");
					response.addCookie(cookie);
					break;
				}
			}
		}
	}
	
	/**
	 * 获取完整的请求路径
	 *
	 * @param request
	 * @return String
	 */
	public static String getRedirectUrl(HttpServletRequest request) {
		
		String httpFullUrl = getHttpFullUrl(request, request.getRequestURI());
		StringBuilder sb1 = new StringBuilder(httpFullUrl);
		Map<String, Object> params = handleServletParameter(request);
		StringBuilder sb2 = new StringBuilder("");
		
		if (params.size() > 0) {
			for (Map.Entry<String, Object> entry : params.entrySet()) {
				if (StringUtils.isNotEmpty(sb2.toString())) {
					sb2.append("&");
				}
				sb2.append(entry.getKey() + "=" + entry.getValue());
			}
			
			if (sb1.indexOf("?") != -1) {
				sb1.append("&");
			} else {
				sb1.append("?");
			}
			sb1.append(sb2);
		}
		return sb1.toString();
	}
	
	/**
	 * @param request
	 * @param url
	 * @return
	 */
	public static String getHttpFullUrl(HttpServletRequest request, String url) {
		String portString = "";
		int port = request.getServerPort();
		if (port != 80 && port != 443) {
			portString = ":" + port;
		}
		return new StringBuilder().append(request.getScheme())
				.append("://")
				.append(request.getServerName())
				.append(portString)
				.append(url)
				.toString();
	}
	
	/**
	 * 把Map所有元素, 按字母排序, 然后按照 "参数=参数值" 的模式用"&"字符拼接成字符串
	 *
	 * @param params 需要签名的参数
	 * @return String URL请求参数字符串
	 */
	public static String toUrlParamStr(Map<String, Object> params) {
		SortedMap<String, Object> sortedMap = new TreeMap<String, Object>(params);
		
		StringBuffer sb = new StringBuffer();
		Set es = sortedMap.entrySet();
		Iterator it = es.iterator();
		
		while (it.hasNext()) {
			Map.Entry sign = (Map.Entry) it.next();
			String k = (String) sign.getKey();
			String v = (String) sign.getValue();
			
			if (StringUtils.isNotEmpty(k) && StringUtils.isNotEmpty(v)) {
				
				if (StringUtils.isNotEmpty(sb.toString())) {
					sb.append("&");
				}
				
				sb.append(k);
				sb.append("=");
				sb.append(v);
			}
		}
		return sb.toString();
	}
	
	private static Map<String, Object> handleServletParameter(HttpServletRequest request) {
		Map<String, String[]> requestParameter = request.getParameterMap();
		
		Map<String, Object> parameter = new HashMap<String, Object>();
		parameter.putAll(requestParameter);
		
		Map<String, Object> requestParameters = new HashMap<String, Object>(0);
		for (Map.Entry<String, Object> m : parameter.entrySet()) {
			String key = m.getKey();
			Object[] obj = (Object[]) parameter.get(key);
			requestParameters.put(key, (obj.length > 1) ? obj : obj[0]);
		}
		return requestParameters;
	}
	
	/**
	 * IP为空或者unknown
	 *
	 * @param ip
	 * @return boolean
	 */
	private static boolean isIpUndetermined(String ip) {
		return ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip);
	}
	
	/**
	 * 判断s是null或者空字符串/只包含空格的字符串
	 *
	 * @param s
	 * @return boolean
	 */
	private static boolean isBlank(String s) {
		return s == null || "".equals(s.trim());
	}
}
