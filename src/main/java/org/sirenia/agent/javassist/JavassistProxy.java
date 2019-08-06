package org.sirenia.agent.javassist;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.sirenia.agent.util.AppClassPoolHolder;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

public class JavassistProxy {
	public static final String methodSuffix = "_proxy";
	//public static final String invokerSuffix = "$invoker";
	private static final Map<String, MethodInvoker> contextMap = new ConcurrentHashMap<>();

	public static void proxy(CtClass ct, MethodFilter filter, MethodInvoker invoker)
			throws NotFoundException, CannotCompileException, ClassNotFoundException, IOException {
		// 解冻
		if(ct.isFrozen()){
			ct.defrost();
		}
		CtMethod[] methods = ct.getDeclaredMethods();// ct.getMethods();
		String uid = UUID.randomUUID().toString();
		for (int i = 0; i < methods.length; i++) {
			CtMethod method = methods[i];
			if (filter != null && !filter.filter(method)) {
				continue;
			}
			String methodName = method.getName();
			//CtField field = CtField.make("private "+MethodInvoker.class.getName()+" "+methodName+invokerSuffix, ct);
			//ct.addField(field);
			CtMethod copyMethod = CtNewMethod.copy(method, method.getName() + methodSuffix, ct, null);
			ct.addMethod(copyMethod);
			int mod = method.getModifiers();
			List<String> body = new ArrayList<>();
			body.add("{");
			if(Modifier.isStatic(mod)){
				body.add("return ($r)$proceed($class,null,"+wrapQuota(methodName)+",$sig,$args,"+wrapQuota(uid)+");");
			}else{
				body.add("return ($r)$proceed($class,$0,"+wrapQuota(methodName)+",$sig,$args,"+wrapQuota(uid)+");");
			}
			body.add("}");
			method.setBody(String.join("\n", body), JavassistProxy.class.getName(), "invoke");
		}
		contextMap.put(uid, invoker);
		//ct.toClass();
		//ct.writeFile();
		//ct.writeFile(CtClass.class.getClassLoader().getResource(".").getFile());
	}

	public static CtClass proxy(String className, MethodFilter filter, MethodInvoker invoker)
			throws NotFoundException, CannotCompileException, ClassNotFoundException, IOException {
		//ClassPool pool = ClassPool.getDefault();
		ClassPool pool = AppClassPoolHolder.get();
		ClassLoader cl = Thread.currentThread().getContextClassLoader();
		pool.appendClassPath(new LoaderClassPath(cl));
		CtClass ct = pool.getCtClass(className);
		// CtClass ct = pool.get(className);
		proxy(ct, filter, invoker);
		return ct;
	};

	private static String wrapQuota(String text) {
		return "\"" + text + "\"";
	}
	public static Object invoke(Class<?> selfClass,Object self,String method,Class<?>[] types,Object[] args,String invokerId) throws Throwable{
		/*Class<?>[] types2 = Stream.of(types).map(type->{
			try {
				//此路不通。HttpServletRequest之前已经被appclassloader加载过，
				//即使我们再调用web容器的classloader去加载，它也会返回appclassloader加载的类。
				return ClassUtil.forName(type.getName(), true, cl);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}).collect(Collectors.toList()).toArray(new Class<?>[0]);*/
		Method thisMethod = selfClass.getDeclaredMethod(method, types);
		Method proceed = selfClass.getDeclaredMethod(method+methodSuffix, types);
		if(!proceed.isAccessible()){
			proceed.setAccessible(true);
		}
		MethodInvoker invoker = contextMap.get(invokerId);
		Object res = invoker.invoke(self, thisMethod, proceed, args);
		return res;
		//return proceed.invoke(self, args);
	}
	
}