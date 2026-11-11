require 'yaml'
require 'json'

mode = ENV.fetch('SCAN_MODE')
raise "Unsupported scan mode: #{mode}" unless ['read', 'write', 'anonymous'].include?(mode)

text = File.read('.github/zap/active-scan-plan.yaml')
['ZAP_AUTH_TOKEN', 'ZAP_TEST_OID', 'ZAP_TEST_FIRM_CODE'].each do |name|
  text = text.gsub("${#{name}}", ENV.fetch(name))
end
plan = YAML.safe_load(text)
context = plan.fetch('env').fetch('contexts').first
target = context.fetch('urls').first
raise 'Scan target must remain local' unless target == 'http://localhost:8080'

write_paths = [
  '/api/v1/commands/users/convert-to-multi-firm',
  '/api/v1/commands/user-profiles/update-offices'
]
write_requests = [
  {
    'url' => target + write_paths[0], 'method' => 'POST',
    'headers' => ['Content-Type: application/json'],
    'data' => JSON.generate('userEntraObjectId' => '00000000-0000-0000-0000-000000000006')
  },
  {
    'url' => target + write_paths[1], 'method' => 'POST',
    'headers' => ['Content-Type: application/json'],
    'data' => JSON.generate(
      'userProfileId' => '00000000-0000-0000-0000-000000000006',
      'officeIds' => ['00000000-0000-0000-0000-000000000003']
    )
  },
  {
    'url' => target + write_paths[1], 'method' => 'POST',
    'headers' => ['Content-Type: application/json'],
    'data' => JSON.generate(
      'userProfileId' => '00000000-0000-0000-0000-000000000006',
      'unrestrictedOfficeAccess' => true
    )
  }
]
requestor = plan.fetch('jobs').find { |job| job['type'] == 'requestor' }
read_requests = requestor.fetch('requests')
baseline = read_requests.dup
if mode == 'write'
  context['includePaths'] = write_paths.map { |path| Regexp.escape(target + path) + '$' }
  requestor['requests'] = write_requests
  # Explicit bodies exercise both variants without an import mutating the fixture first.
  plan['jobs'].reject! { |job| job['type'] == 'openapi' }
  baseline += write_requests
elsif mode == 'anonymous'
  raise 'Anonymous pass must not receive a token' unless ENV.fetch('ZAP_AUTH_TOKEN').empty?

  plan['jobs'].reject! { |job| job['type'] == 'replacer' }
  context['includePaths'] += write_paths.map { |path| Regexp.escape(target + path) + '$' }
  requestor['requests'] += write_requests
  baseline += write_requests
end

if mode != 'write'
  spec = YAML.load_file('laa-data-user.yml')
  scope = context.fetch('includePaths').map { |pattern| Regexp.new(pattern) }
  spec['paths'] = spec.fetch('paths').each_with_object({}) do |(path, item), paths|
    next unless scope.any? { |pattern| pattern.match?(target + path) }

    methods = item.select do |method, _|
      method == 'get' || (mode == 'anonymous' && method == 'post' && write_paths.include?(path))
    end
    next if methods.empty?

    paths[path] = item.select { |key, _| key == 'parameters' }.merge(methods)
    methods.each_value do |operation|
      operation.delete('servers')
      operation['security'] = [] if mode == 'anonymous'
    end
  end
  raise 'No in-scope operations found in the OpenAPI specification' if spec['paths'].empty?

  parameters = spec.fetch('components').fetch('parameters').values
  spec['paths'].each_value do |item|
    parameters.concat(item.fetch('parameters', []))
    ['get', 'post'].each do |method|
      parameters.concat(item.fetch(method, {}).fetch('parameters', []))
    end
  end
  parameters.each do |parameter|
    schema = parameter['schema']
    next unless schema

    if parameter['name'] == 'sort_by'
      schema.fetch('items')['default'] = 'lastName,asc'
      next
    end
    value = case parameter['name']
            when 'firmId', 'firm_id' then ENV.fetch('ZAP_TEST_FIRM_CODE')
            when 'query', 'firm', 'user' then 'ZAP'
            when 'email_query' then 'zap-scan@example.invalid'
            else ENV.fetch('ZAP_TEST_OID') if schema['format'] == 'uuid'
            end
    schema['default'] = value unless value.nil?
  end
  spec['servers'] = [{ 'url' => target }]
  spec['security'] = [] if mode == 'anonymous'
  File.write('build/zap/openapi-read-only.json', JSON.pretty_generate(spec))
  puts "Prepared #{spec['paths'].length} in-scope OpenAPI paths for #{mode}"
end

File.write('build/zap/plan.yaml', YAML.dump(plan))
File.write('build/zap/read-baseline-requests.json', JSON.pretty_generate(baseline))
