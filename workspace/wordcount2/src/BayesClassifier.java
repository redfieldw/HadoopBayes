import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;


public class BayesClassifier
{
	// 获取频数文件的 MAP-REDUCE
	public static class TokenizerMapper extends Mapper<Object, Text, Text, IntWritable>{
		
		private final static IntWritable one = new IntWritable(1);
		private Text word = new Text();
		
		// value: 每一行的内容
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException{
			
			String IsPraise = new String();	// define a signal of IsPraise or IsNotPraise
			StringTokenizer itr = new StringTokenizer(value.toString());
			// IsPraise or not, 处理位于每一行第一个词的 label
			if(itr.hasMoreTokens()){
				IsPraise = itr.nextToken().toString();	// 获取第一个label
				IsPraise = (IsPraise.equals("好评")) ?  "IsPraise" : "IsNotPraise";
				word.set("IsPraise" + IsPraise);
				context.write(word, one);
			}
			while(itr.hasMoreTokens())
			{	
				// 过滤
				String thisWord = itr.nextToken();
				if (thisWord.contains("!") || thisWord.contains("@") || thisWord.contains("#") || thisWord.contains("$") 
						|| thisWord.contains("%") || thisWord.contains("^") || thisWord.contains("&") 
						|| thisWord.contains("*") || thisWord.contains("(") || thisWord.contains(")")
						|| thisWord.contains("-") || thisWord.contains("+") || thisWord.contains("_")
						|| thisWord.contains("=") || thisWord.contains("[") || thisWord.contains("]")
						|| thisWord.contains("{") || thisWord.contains("\\") || thisWord.contains("'")
						|| thisWord.contains("}") || thisWord.contains(";") || thisWord.contains("\"")
						|| thisWord.contains("|") || thisWord.contains(":") || thisWord.contains(",")
						|| thisWord.contains("<") || thisWord.contains(".") || thisWord.contains("＼")){
					return;
				}
				word.set(thisWord + IsPraise);
				context.write(word, one);
			}
		}
	}
	
	public static class IntSumReducer extends Reducer<Text, IntWritable, Text, IntWritable>{
		private IntWritable result = new IntWritable();
		
		public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException{
			int sum = 0;
			for (IntWritable val : values)
			{
				sum += val.get();
			}
			result.set(sum);
			context.write(key, result);
		}
	}
	
	// 预测的 mapper
	public static class BayesPredictMapper extends Mapper<Object, Text, Text, IntWritable>{
		private final static IntWritable one = new IntWritable(1);
		private Text word = new Text();
		public Map<String, Integer> featureFrequency = null;
		public static int fileCount = 0;													// 用于输出文件、记录当前行数
		
		// value: 每一行的内容
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException{	
			String IsPraise = new String();	// define a signal of IsPraise or IsNotPraise
			
			// 将全局变量取出来反序列化为MAP
			Configuration conf = context.getConfiguration();
			if(featureFrequency == null){
				String featureFrequencyJson = conf.get("featureFrequencyJson");
				featureFrequency = JSON.parseObject(featureFrequencyJson, new TypeReference<Map<String, Integer>>(){});	// 反序列化
			}
			
			// 统计数量
			Double AllIsPraiseCount = 1.0 * featureFrequency.get("IsPraiseIsPraise");		// 训练集中好评的数量
			Double AllIsNotPraiseCount = 1.0 * featureFrequency.get("IsPraiseIsNotPraise");	// 训练集中差评的数量
			Double AllCount = AllIsPraiseCount + AllIsNotPraiseCount;						// 训练集总数
			
			// 应用贝叶斯公式、详见文档
			Double IsPraiseProb = 1.0 * (AllIsPraiseCount/AllCount);						// 贝叶斯公式第一步
			Double IsNotPraiseProb = 1.0 * (AllIsNotPraiseCount/AllCount);
			
			BigDecimal IsPProbedecimal = new BigDecimal(IsPraiseProb);						// 将double转换为精度更高的BIgDecimal
			BigDecimal IsNPProbedecimal = new BigDecimal(IsNotPraiseProb);
			
			
			String IsThisPraise = "";														// 本条记录的好坏
						
			StringTokenizer itr = new StringTokenizer(value.toString());
			if(itr.hasMoreTokens()){
				IsThisPraise = (itr.nextToken().equals("好评")) ? "好评" : "差评";				// 先获取本条记录的好坏
			}
			
			while(itr.hasMoreTokens())
			{
				String itrWord = itr.nextToken();
				// 平滑处理、如果没有这个值就给他 1
				Double itrWordIsPraiseCount = 1.0 * ((featureFrequency.containsKey((itrWord + "IsPraise"))) ? featureFrequency.get(itrWord + "IsPraise") : 0);
				Double itrWordIsNotPraiseCount = 1.0 * ((featureFrequency.containsKey((itrWord + "IsNotPraise"))) ? featureFrequency.get(itrWord + "IsNotPraise") : 0);

				IsPProbedecimal = IsPProbedecimal.multiply(new BigDecimal(itrWordIsPraiseCount / AllIsPraiseCount));
				IsNPProbedecimal = IsNPProbedecimal.multiply(new BigDecimal(itrWordIsNotPraiseCount / AllIsNotPraiseCount));
			}
			IsPraise = (IsPProbedecimal.compareTo(IsNPProbedecimal) == 1) ? "好评" : "差评";	// 判断预测结果
			fileCount++;
			word.set(fileCount + "\t" + IsThisPraise + "\t" + IsPraise);
			context.write(word, one);
		}
	}
}
