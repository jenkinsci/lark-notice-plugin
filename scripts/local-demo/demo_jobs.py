#!/usr/bin/env python3
"""Create, build and delete the lark-notice demo jobs on a local Jenkins.

Intended for manual verification against a `mvn hpi:run` instance whose robots are
already configured. Robot ids are read from the JENKINS_HOME global config so the
script keeps working after the robots are recreated with fresh ids.

    python3 scripts/local-demo/demo_jobs.py create
    python3 scripts/local-demo/demo_jobs.py create --replace
    python3 scripts/local-demo/demo_jobs.py build
    python3 scripts/local-demo/demo_jobs.py build lark-robot-pipeline --quiet
    python3 scripts/local-demo/demo_jobs.py delete

Run it from the plugin directory (the default --home is ./work). Options: --jenkins URL
(default http://localhost:8080/jenkins), --home DIR, --user user:apiToken when the
instance requires auth, --replace to overwrite existing jobs, --quiet to skip the
console dump, --timeout SECONDS, --lark/--ding/--wecom ID to override robot discovery.
Job names must come straight after the command, before any option.

The three single-protocol pipelines call only this plugin's own steps, so they also work
on a bare instance. `all-robots-declarative` needs pipeline-model-definition,
workflow-basic-steps and workflow-durable-task-step — the pom declares them as test
dependencies, which keeps them out of Plugin-Dependencies while still letting `hpi:run`
install them. If the declarative job fails with "Unknown stage section", restart
`hpi:run` so those plugins get installed into JENKINS_HOME/plugins.
"""
import argparse
import base64
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import http.cookiejar
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import escape

GLOBAL_CONFIG = "io.jenkins.plugins.lark.notice.config.LarkGlobalConfig.xml"
PROTOCOLS = {"lark": "LARK_COMPATIBLE", "ding": "DING_TALK", "wecom": "WECHAT_WORK"}
DISPLAY_NAMES = {"lark": "飞书机器人", "ding": "钉钉机器人", "wecom": "企微机器人"}


class Jenkins:
    """Minimal Jenkins REST client: cookie-backed session plus a fresh CSRF crumb."""

    def __init__(self, root, credentials=None):
        self.root = root.rstrip("/")
        self.credentials = credentials
        self.opener = urllib.request.build_opener(
            urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

    def _request(self, path, data=None, method="GET", content_type=None, crumb=True):
        req = urllib.request.Request(self.root + path, data=data, method=method)
        if self.credentials:
            token = base64.b64encode(self.credentials.encode()).decode()
            req.add_header("Authorization", "Basic " + token)
        if content_type:
            req.add_header("Content-Type", content_type)
        if method == "POST" and crumb:
            field, value = self.crumb()
            req.add_header(field, value)
        return self.opener.open(req)

    def crumb(self):
        """Fetches a crumb per request: it is bound to this client's session cookie."""
        with self._request("/crumbIssuer/api/json") as response:
            payload = json.load(response)
        return payload["crumbRequestField"], payload["crumb"]

    def get(self, path):
        with self._request(path) as response:
            return response.read().decode("utf-8", "replace")

    def post(self, path, data=b"", content_type=None):
        with self._request(path, data=data, method="POST", content_type=content_type) as response:
            return response.status

    def job_exists(self, name):
        try:
            self.get(f"/job/{urllib.parse.quote(name)}/api/json?tree=name")
            return True
        except urllib.error.HTTPError as error:
            if error.code == 404:
                return False
            raise


def discover_robots(home, overrides):
    """Maps lark/ding/wecom to a robot id, preferring explicit overrides.

    Reads only ids, names and protocol types out of the global config — webhooks and
    security values stay untouched so nothing secret reaches stdout.
    """
    robots = {key: value for key, value in overrides.items() if value}
    missing = [key for key in PROTOCOLS if key not in robots]
    if not missing:
        return robots

    config = Path(home) / GLOBAL_CONFIG
    if not config.is_file():
        sys.exit(f"未找到全局配置 {config}，请用 --lark/--ding/--wecom 指定机器人 id")

    by_protocol = {}
    for element in ET.parse(config).getroot().iter(
            "io.jenkins.plugins.lark.notice.config.LarkRobotConfig"):
        protocol = element.findtext("protocolType")
        by_protocol.setdefault(protocol, (element.findtext("id"), element.findtext("name")))

    for key in missing:
        found = by_protocol.get(PROTOCOLS[key])
        if not found:
            sys.exit(f"全局配置里没有 {PROTOCOLS[key]} 机器人，请先配置或用 --{key} 指定 id")
        robots[key] = found[0]
        print(f"发现 {PROTOCOLS[key]} 机器人：{found[1]} ({found[0]})")
    return robots


LARK_SCRIPT = """// 飞书机器人（LARK_COMPATIBLE）：TEXT / MARKDOWN / POST / CARD
def robot = '{robot}'
def job = "${{env.JOB_NAME}} #${{env.BUILD_NUMBER}}"

lark robot: robot, type: 'TEXT',
        text: ["飞书 TEXT 消息", "作业：${{job}}"]

lark robot: robot, type: 'MARKDOWN', title: '飞书 MARKDOWN 消息',
        text: ["**作业**：[${{job}}](${{env.BUILD_URL}})", "**消息类型**：MARKDOWN"]

lark robot: robot, type: 'POST', title: '飞书 POST 富文本',
        post: [[[tag: 'text', text: '第一行：富文本段落 '],
                [tag: 'a', text: '打开构建', href: "${{env.BUILD_URL}}"]],
               [[tag: 'text', text: '第二行：由 lark step 发送']]]

lark robot: robot, type: 'CARD', title: '飞书 CARD 卡片',
        text: ['卡片正文，附带自定义字段与按钮'],
        cardFields: [[keyname: '插件版本', value: '{version}'],
                     [keyname: '构建地址', value: '控制台', url: "${{env.BUILD_URL}}console"]],
        buttons: [[title: '查看构建', url: "${{env.BUILD_URL}}", type: 'primary'],
                  [title: '控制台日志', url: "${{env.BUILD_URL}}console", type: 'default']]
"""

DING_SCRIPT = """// 钉钉机器人（DING_TALK）：TEXT / MARKDOWN / LINK / CARD / FEED_CARD
def robot = '{robot}'
def job = "${{env.JOB_NAME}} #${{env.BUILD_NUMBER}}"

dingTalk robot: robot, type: 'TEXT',
        text: ["钉钉 TEXT 消息", "作业：${{job}}"]

dingTalk robot: robot, type: 'MARKDOWN', title: '钉钉 MARKDOWN 消息',
        text: ["### ${{job}}", "- **消息类型**：MARKDOWN", "- [打开构建](${{env.BUILD_URL}})"]

dingTalk robot: robot, type: 'LINK', title: '钉钉 LINK 消息',
        text: ['点击卡片跳转到本次构建'], messageUrl: "${{env.BUILD_URL}}"

dingTalk robot: robot, type: 'CARD', title: '钉钉 CARD 卡片',
        text: ["**${{job}}**", '', 'ActionCard，带纵向按钮'],
        verticalButton: true, hideAvatar: false,
        buttons: [[title: '查看构建', url: "${{env.BUILD_URL}}"],
                  [title: '控制台日志', url: "${{env.BUILD_URL}}console"]]

dingTalk robot: robot, type: 'FEED_CARD',
        feedCardLinks: [[title: '本次构建', messageUrl: "${{env.BUILD_URL}}"],
                        [title: '控制台日志', messageUrl: "${{env.BUILD_URL}}console"],
                        [title: 'Jenkins 首页', messageUrl: "${{env.JENKINS_URL}}"]]
"""

WECOM_SCRIPT = """// 企微机器人（WECHAT_WORK）：TEXT / MARKDOWN / LINK / CARD
def robot = '{robot}'
def job = "${{env.JOB_NAME}} #${{env.BUILD_NUMBER}}"

wechatWork robot: robot, type: 'TEXT',
        text: ["企微 TEXT 消息", "作业：${{job}}"]

wechatWork robot: robot, type: 'MARKDOWN', title: '企微 MARKDOWN 消息',
        text: ["### ${{job}}", "> **消息类型**：MARKDOWN", "[打开构建](${{env.BUILD_URL}})"]

// 企微没有 link 消息类型，WechatWorkMessageSender.sendLink 会降级成 markdown，
// 降级时 messageUrl 不会拼进正文，所以链接要自己写在 text 里。
wechatWork robot: robot, type: 'LINK', title: '企微 LINK 图文（降级为 markdown）',
        text: ['news 图文消息在企微上降级发送', "[打开构建](${{env.BUILD_URL}})"],
        messageUrl: "${{env.BUILD_URL}}"

// 来源图标（source.icon_url）与图文图片（card_image.url）都必须是企微能访问的公网
// http(s) 图片地址，这里写的是插件内置默认值，换成自己的图即可。
wechatWork robot: robot, type: 'CARD', title: '企微 CARD 模板卡片',
        text: ["**${{job}}**", '模板卡片，带来源、引用区与按钮'],
        sourceDesc: 'Jenkins lark-notice',
        sourceIconUrl: 'https://get.jenkins.io/art/jenkins-logo/favicon.ico',
        cardImageUrl: 'https://www.jenkins.io/images/post-images/2025/07/24/redesigning-jenkins-part-two.png',
        quoteTitle: '构建摘要', quoteText: '本次构建由脚本式流水线触发',
        quoteUrl: "${{env.BUILD_URL}}",
        cardFields: [[keyname: '插件版本', value: '{version}'],
                     [keyname: '消息类型', value: 'CARD']],
        buttons: [[title: '查看构建', url: "${{env.BUILD_URL}}", type: 'primary'],
                  [title: '控制台日志', url: "${{env.BUILD_URL}}console"]]
"""

FREESTYLE_COMMAND = 'echo "build ${BUILD_NUMBER} of ${JOB_NAME}"\nsleep 2\n'

DECLARATIVE_SCRIPT = """// 声明式流水线：需要 pipeline-model-definition / workflow-basic-steps /
// workflow-durable-task-step，pom 里已按 test 作用域声明，`hpi:run` 会一并安装。
pipeline {{
    agent any

    options {{
        // 只用 Jenkins 核心与声明式自带的选项，避免再引入 timestamper 之类的插件。
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }}

    environment {{
        LARK_ROBOT = '{lark}'
        DING_ROBOT = '{ding}'
        WECOM_ROBOT = '{wecom}'
        PLUGIN_VERSION = '{version}'
    }}

    stages {{
        stage('准备') {{
            steps {{
                echo "声明式流水线开始：${{env.JOB_NAME}} #${{env.BUILD_NUMBER}}"
                sh 'echo declarative pipeline demo && sleep 1'
            }}
        }}

        stage('顺序通知') {{
            steps {{
                lark robot: env.LARK_ROBOT, type: 'MARKDOWN', title: '声明式流水线 · 飞书',
                        text: ["**阶段**：顺序通知", "[打开构建](${{env.BUILD_URL}})"]
                dingTalk robot: env.DING_ROBOT, type: 'MARKDOWN', title: '声明式流水线 · 钉钉',
                        text: ["### ${{env.JOB_NAME}} #${{env.BUILD_NUMBER}}",
                               "- **阶段**：顺序通知", "- [打开构建](${{env.BUILD_URL}})"]
                wechatWork robot: env.WECOM_ROBOT, type: 'MARKDOWN', title: '声明式流水线 · 企微',
                        text: ["### ${{env.JOB_NAME}} #${{env.BUILD_NUMBER}}",
                               "> **阶段**：顺序通知", "[打开构建](${{env.BUILD_URL}})"]
            }}
        }}

        stage('并行卡片') {{
            parallel {{
                stage('飞书卡片') {{
                    steps {{
                        lark robot: env.LARK_ROBOT, type: 'CARD', title: '声明式 · 飞书卡片',
                                text: ['parallel 分支内发送卡片'],
                                cardFields: [[keyname: '插件版本', value: env.PLUGIN_VERSION],
                                             [keyname: '阶段', value: '并行卡片']],
                                buttons: [[title: '查看构建', url: "${{env.BUILD_URL}}",
                                           type: 'primary']]
                    }}
                }}
                stage('钉钉卡片') {{
                    steps {{
                        dingTalk robot: env.DING_ROBOT, type: 'CARD', title: '声明式 · 钉钉卡片',
                                text: ["**${{env.JOB_NAME}} #${{env.BUILD_NUMBER}}**", '',
                                       'parallel 分支内发送 ActionCard'],
                                verticalButton: true,
                                buttons: [[title: '查看构建', url: "${{env.BUILD_URL}}"]]
                    }}
                }}
                stage('企微卡片') {{
                    steps {{
                        wechatWork robot: env.WECOM_ROBOT, type: 'CARD', title: '声明式 · 企微卡片',
                                text: ['parallel 分支内发送模板卡片'],
                                sourceDesc: 'Jenkins lark-notice',
                                cardFields: [[keyname: '插件版本', value: env.PLUGIN_VERSION],
                                             [keyname: '阶段', value: '并行卡片']],
                                buttons: [[title: '查看构建', url: "${{env.BUILD_URL}}",
                                           type: 'primary']]
                    }}
                }}
            }}
        }}

        stage('容错发送') {{
            steps {{
                // failOnError: false 时机器人 id 不存在也只告警，不让 stage 失败。
                lark robot: 'robot-does-not-exist', type: 'TEXT', failOnError: false,
                        text: ['这条会发送失败，但 failOnError: false 让构建继续']
            }}
        }}
    }}

    post {{
        success {{
            lark robot: env.LARK_ROBOT, type: 'TEXT',
                    text: ["✅ ${{env.JOB_NAME}} #${{env.BUILD_NUMBER}} 构建成功"]
        }}
        failure {{
            lark robot: env.LARK_ROBOT, type: 'TEXT',
                    text: ["❌ ${{env.JOB_NAME}} #${{env.BUILD_NUMBER}} 构建失败"]
        }}
        always {{
            echo "声明式流水线结束：${{currentBuild.currentResult}}"
        }}
    }}
}}
"""


def pipeline_xml(description, script):
    return f"""<?xml version='1.1' encoding='UTF-8'?>
<flow-definition plugin="workflow-job">
  <description>{escape(description)}</description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition" plugin="workflow-cps">
    <script>{escape(script)}</script>
    <sandbox>true</sandbox>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>
"""


def notifier_config_xml(robot_id, robot_name):
    return f"""        <io.jenkins.plugins.lark.notice.config.LarkNotifierConfig>
          <raw>false</raw>
          <disabled>false</disabled>
          <checked>true</checked>
          <robotId>{escape(robot_id)}</robotId>
          <robotName>{escape(robot_name)}</robotName>
          <atAll>false</atAll>
          <noticeOccasions>
            <string>START</string>
            <string>SUCCESS</string>
            <string>FAILURE</string>
            <string>ABORTED</string>
            <string>UNSTABLE</string>
          </noticeOccasions>
        </io.jenkins.plugins.lark.notice.config.LarkNotifierConfig>"""


def freestyle_xml(description, command, robots, version):
    configs = "\n".join(notifier_config_xml(robots[key], DISPLAY_NAMES[key]) for key in PROTOCOLS)
    return f"""<?xml version='1.1' encoding='UTF-8'?>
<project>
  <actions/>
  <description>{escape(description)}</description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <scm class="hudson.scm.NullSCM"/>
  <canRoam>true</canRoam>
  <disabled>false</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers/>
  <concurrentBuild>false</concurrentBuild>
  <builders>
    <hudson.tasks.Shell>
      <command>{escape(command)}</command>
      <configuredLocalRules/>
    </hudson.tasks.Shell>
  </builders>
  <publishers>
    <io.jenkins.plugins.lark.notice.config.LarkNotifier plugin="lark-notice@{escape(version)}">
      <larkNotifierConfigs>
{configs}
      </larkNotifierConfigs>
    </io.jenkins.plugins.lark.notice.config.LarkNotifier>
  </publishers>
  <buildWrappers/>
</project>
"""


def job_specs(robots, version):
    """Builds the job name -> config XML mapping for the four demo jobs."""
    return {
        "lark-robot-pipeline": pipeline_xml(
            "飞书机器人（LARK_COMPATIBLE）消息类型演示：TEXT / MARKDOWN / POST / CARD",
            LARK_SCRIPT.format(robot=robots["lark"], version=version)),
        "dingtalk-robot-pipeline": pipeline_xml(
            "钉钉机器人（DING_TALK）消息类型演示：TEXT / MARKDOWN / LINK / CARD / FEED_CARD",
            DING_SCRIPT.format(robot=robots["ding"], version=version)),
        "wechatwork-robot-pipeline": pipeline_xml(
            "企微机器人（WECHAT_WORK）消息类型演示：TEXT / MARKDOWN / LINK / CARD",
            WECOM_SCRIPT.format(robot=robots["wecom"], version=version)),
        "all-robots-freestyle": freestyle_xml(
            "自由风格作业：构建后步骤同时通知飞书 / 钉钉 / 企微三个机器人",
            FREESTYLE_COMMAND, robots, version),
        "all-robots-declarative": pipeline_xml(
            "声明式流水线：environment / parallel / post 中调用三个机器人的 step",
            DECLARATIVE_SCRIPT.format(version=version, **robots)),
    }


def plugin_version(default="2.1.10"):
    """Reads <revision> out of the sibling pom so the config XML claims the real version."""
    pom = Path(__file__).resolve().parents[2] / "pom.xml"
    if not pom.is_file():
        return default
    namespace = {"m": "http://maven.apache.org/POM/4.0.0"}
    revision = ET.parse(pom).getroot().findtext("m:properties/m:revision", namespaces=namespace)
    return revision or default


def create(jenkins, specs, replace):
    for name, xml in specs.items():
        data = xml.encode("utf-8")
        content_type = "application/xml; charset=utf-8"
        exists = jenkins.job_exists(name)
        if exists and not replace:
            print(f"{name}: 已存在，跳过（加 --replace 覆盖）")
            continue
        try:
            if exists:
                status = jenkins.post(f"/job/{urllib.parse.quote(name)}/config.xml",
                                      data, content_type)
                print(f"{name}: 已更新（HTTP {status}）")
            else:
                query = urllib.parse.urlencode({"name": name})
                status = jenkins.post(f"/createItem?{query}", data, content_type)
                print(f"{name}: 已创建（HTTP {status}）")
        except urllib.error.HTTPError as error:
            body = error.read().decode("utf-8", "replace")[:500]
            print(f"{name}: 失败 HTTP {error.code}\n{body}")


def delete(jenkins, names):
    for name in names:
        if not jenkins.job_exists(name):
            print(f"{name}: 不存在，跳过")
            continue
        status = jenkins.post(f"/job/{urllib.parse.quote(name)}/doDelete")
        print(f"{name}: 已删除（HTTP {status}）")


def build(jenkins, names, timeout, quiet):
    """Triggers each job, waits for it to finish, then prints its console log."""
    for name in names:
        if not jenkins.job_exists(name):
            print(f"{name}: 不存在，跳过（先执行 create）")
            continue
        previous = last_build(jenkins, name)
        previous_number = previous["number"] if previous else 0
        status = jenkins.post(f"/job/{urllib.parse.quote(name)}/build?delay=0sec")
        print(f"{name}: 已触发（HTTP {status}）")
        last = wait_for_build(jenkins, name, previous_number, timeout)
        if last is None:
            print(f"{name}: 等待 {timeout}s 仍未结束，请到页面上查看")
            continue
        print(f"\n{'=' * 70}\n{name} #{last['number']} -> {last['result']}\n{'=' * 70}")
        if not quiet:
            print(jenkins.get(f"/job/{urllib.parse.quote(name)}/{last['number']}/consoleText"))


def last_build(jenkins, name):
    tree = "lastBuild%5Bnumber,building,result%5D"
    info = json.loads(jenkins.get(f"/job/{urllib.parse.quote(name)}/api/json?tree={tree}"))
    return info.get("lastBuild")


def wait_for_build(jenkins, name, after_number, timeout):
    """Waits for a build newer than `after_number` to finish.

    The number check matters: right after the trigger the run is still queued, so
    `lastBuild` would otherwise report the previous — already finished — build.
    """
    deadline = time.time() + timeout
    while time.time() < deadline:
        last = last_build(jenkins, name)
        if last and last["number"] > after_number and not last["building"] and last["result"]:
            return last
        time.sleep(3)
    return None


def main():
    parser = argparse.ArgumentParser(
        description=__doc__.splitlines()[0],
        epilog="可用作业：lark-robot-pipeline、dingtalk-robot-pipeline、wechatwork-robot-pipeline、"
               "all-robots-freestyle、all-robots-declarative。作业名必须紧跟命令再写选项，例如 "
               "`create lark-robot-pipeline --replace`；argparse 不接受选项夹在两个位置参数之间。")
    parser.add_argument("command", choices=["create", "build", "delete"])
    parser.add_argument("jobs", nargs="*", help="仅处理这些作业，默认全部")
    parser.add_argument("--jenkins", default="http://localhost:8080/jenkins",
                        help="Jenkins 根地址，默认 http://localhost:8080/jenkins")
    parser.add_argument("--home", default="work", help="JENKINS_HOME 路径，默认 ./work")
    parser.add_argument("--user", help="需要鉴权时传 user:apiToken")
    parser.add_argument("--replace", action="store_true", help="create 时覆盖已存在的作业")
    parser.add_argument("--quiet", action="store_true", help="build 时不打印控制台日志")
    parser.add_argument("--timeout", type=int, default=180, help="等待单次构建结束的秒数")
    for key in PROTOCOLS:
        parser.add_argument(f"--{key}", help=f"手动指定 {PROTOCOLS[key]} 机器人 id")
    args = parser.parse_args()

    jenkins = Jenkins(args.jenkins, args.user)
    if args.command == "delete":
        names = args.jobs or list(job_specs({key: "" for key in PROTOCOLS}, "0").keys())
        delete(jenkins, names)
        return

    robots = discover_robots(args.home, {key: getattr(args, key) for key in PROTOCOLS})
    specs = job_specs(robots, plugin_version())
    if args.jobs:
        unknown = [name for name in args.jobs if name not in specs]
        if unknown:
            sys.exit(f"未知作业：{', '.join(unknown)}；可用：{', '.join(specs)}")
        specs = {name: specs[name] for name in args.jobs}

    if args.command == "create":
        create(jenkins, specs, args.replace)
    else:
        build(jenkins, list(specs), args.timeout, args.quiet)


if __name__ == "__main__":
    main()
