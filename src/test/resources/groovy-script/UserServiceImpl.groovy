import com.alibaba.fastjson.JSONObject
class HelloServiceImpl{
	def logger = org.slf4j.LoggerFactory.getLogger 'HelloServiceImpl#groovy'
	def login222(name,pwd){
		new org.wt.model.User(name:'kakaxi',password:'123',nick:'qimuwuwukai')
	}
	def hello(){
		logger.info 'hello'+"*"*10
	}
}
