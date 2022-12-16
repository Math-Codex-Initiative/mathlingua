/*
 * Copyright 2022 Dominic Kramer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package phase5

import (
	"fmt"
	"mathlingua/internal/ast"
	"mathlingua/internal/frontend"
	"mathlingua/internal/frontend/formulation"
	"mathlingua/internal/frontend/phase4"
)

func Parse(root phase4.Root, tracker frontend.DiagnosticTracker) (ast.Document, bool) {
	p := parser{
		tracker: tracker,
	}
	return p.toDocument(root)
}

//////////////////////////////////////////////////////////////////////////

type parser struct {
	tracker frontend.DiagnosticTracker
}

//////////////////////////// given ///////////////////////////////////////

func (p *parser) toGivenGroup(group phase4.Group) (ast.GivenGroup, bool) {
	if !startsWithSections(group, ast.LowerGivenName) {
		return ast.GivenGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.GivenSections...)
	if !ok {
		return ast.GivenGroup{}, false
	}
	given := *p.toGivenSection(sections[ast.LowerGivenName])
	var where *ast.WhereSection
	if sect, ok := sections[ast.LowerWhereName]; ok {
		where = p.toWhereSection(sect)
	}
	var suchThat *ast.SuchThatSection
	if sect, ok := sections[ast.LowerSuchThatName]; ok {
		suchThat = p.toSuchThatSection(sect)
	}
	then := *p.toThenSection(sections[ast.LowerThenName])
	return ast.GivenGroup{
		Given:    given,
		Where:    where,
		SuchThat: suchThat,
		Then:     then,
	}, true
}

/////////////////////////// allOf ///////////////////////////////////////

func (p *parser) toAllOfGroup(group phase4.Group) (ast.AllOfGroup, bool) {
	if !startsWithSections(group, ast.LowerAllOfName) {
		return ast.AllOfGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.AllOfSections...)
	if !ok {
		return ast.AllOfGroup{}, false
	}
	return ast.AllOfGroup{
		AllOf: *p.toAllOfSection(sections[ast.LowerAllOfName]),
	}, true
}

func (p *parser) toAllOfSection(section phase4.Section) *ast.AllOfSection {
	return &ast.AllOfSection{
		Clauses: p.oneOrMoreClauses(section),
	}
}

/////////////////////////////// not //////////////////////////////////////

func (p *parser) toNotGroup(group phase4.Group) (ast.NotGroup, bool) {
	if !startsWithSections(group, ast.LowerNotName) {
		return ast.NotGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.NotSections...)
	if !ok {
		return ast.NotGroup{}, false
	}
	return ast.NotGroup{
		Not: *p.toNotSection(sections[ast.LowerNotName]),
	}, true
}

func (p *parser) toNotSection(section phase4.Section) *ast.NotSection {
	return &ast.NotSection{
		Clause: p.exactlyOneClause(section),
	}
}

//////////////////////////////////// anyOf ////////////////////////////////

func (p *parser) toAnyOfGroup(group phase4.Group) (ast.AnyOfGroup, bool) {
	if !startsWithSections(group, ast.LowerAnyOfName) {
		return ast.AnyOfGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.AnyOfSections...)
	if !ok {
		return ast.AnyOfGroup{}, false
	}
	return ast.AnyOfGroup{
		AnyOf: *p.toAnyOfSection(sections[ast.LowerAnyOfName]),
	}, true
}

func (p *parser) toAnyOfSection(section phase4.Section) *ast.AnyOfSection {
	return &ast.AnyOfSection{
		Clauses: p.oneOrMoreClauses(section),
	}
}

////////////////////////////////////// oneOf /////////////////////////////

func (p *parser) toOneOfGroup(group phase4.Group) (ast.OneOfGroup, bool) {
	if !startsWithSections(group, ast.LowerOneOfName) {
		return ast.OneOfGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.OneOfSections...)
	if !ok {
		return ast.OneOfGroup{}, false
	}
	return ast.OneOfGroup{
		OneOf: *p.toOneOfSection(sections[ast.LowerOneOfName]),
	}, true
}

func (p *parser) toOneOfSection(section phase4.Section) *ast.OneOfSection {
	return &ast.OneOfSection{
		Clauses: p.oneOrMoreClauses(section),
	}
}

/////////////////////////////// exists //////////////////////////////////

func (p *parser) toExistsGroup(group phase4.Group) (ast.ExistsGroup, bool) {
	if !startsWithSections(group, ast.LowerExistsName) {
		return ast.ExistsGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.ExistsSections...)
	if !ok {
		return ast.ExistsGroup{}, false
	}
	exists := *p.toExistsSection(sections[ast.LowerExistsName])
	var where *ast.WhereSection
	if sect, ok := sections[ast.LowerWhereName]; ok {
		where = p.toWhereSection(sect)
	}
	var suchThat *ast.SuchThatSection
	if sect, ok := sections[ast.LowerSuchThatName]; ok {
		suchThat = p.toSuchThatSection(sect)
	}
	return ast.ExistsGroup{
		Exists:   exists,
		Where:    where,
		SuchThat: suchThat,
	}, true
}

func (p *parser) toExistsSection(section phase4.Section) *ast.ExistsSection {
	return &ast.ExistsSection{
		Targets: p.oneOrMoreTargets(section),
	}
}

func (p *parser) toWhereSection(section phase4.Section) *ast.WhereSection {
	return &ast.WhereSection{
		Specs: p.oneOrMoreSpecs(section),
	}
}

func (p *parser) toSuchThatSection(section phase4.Section) *ast.SuchThatSection {
	return &ast.SuchThatSection{
		Clauses: p.oneOrMoreClauses(section),
	}
}

/////////////////////////////// existsUnique //////////////////////////////////

func (p *parser) toExistsUniqueGroup(group phase4.Group) (ast.ExistsUniqueGroup, bool) {
	if !startsWithSections(group, ast.LowerExistsUniqueName) {
		return ast.ExistsUniqueGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.ExistsUniqueSections...)
	if !ok {
		return ast.ExistsUniqueGroup{}, false
	}
	existsUnique := *p.toExistsUniqueSection(sections[ast.LowerExistsUniqueName])
	var where *ast.WhereSection
	if sect, ok := sections[ast.LowerWhereName]; ok {
		where = p.toWhereSection(sect)
	}
	suchThat := *p.toSuchThatSection(sections[ast.LowerSuchThatName])
	return ast.ExistsUniqueGroup{
		ExistsUnique: existsUnique,
		Where:        where,
		SuchThat:     suchThat,
	}, true
}

func (p *parser) toExistsUniqueSection(section phase4.Section) *ast.ExistsUniqueSection {
	return &ast.ExistsUniqueSection{
		Targets: p.oneOrMoreTargets(section),
	}
}

///////////////////////// forAll /////////////////////////////////////////

func (p *parser) toForAllGroup(group phase4.Group) (ast.ForAllGroup, bool) {
	if !startsWithSections(group, ast.LowerForAllName) {
		return ast.ForAllGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.ForAllSections...)
	if !ok {
		return ast.ForAllGroup{}, false
	}
	forAll := *p.toForAllSection(sections[ast.LowerForAllName])
	var where *ast.WhereSection
	if sec, ok := sections[ast.LowerWhereName]; ok {
		where = p.toWhereSection(sec)
	}
	var suchThat *ast.SuchThatSection
	if sec, ok := sections[ast.LowerSuchThatName]; ok {
		suchThat = p.toSuchThatSection(sec)
	}
	then := *p.toThenSection(sections[ast.LowerThenName])
	return ast.ForAllGroup{
		ForAll:   forAll,
		Where:    where,
		SuchThat: suchThat,
		Then:     then,
	}, true
}

func (p *parser) toForAllSection(section phase4.Section) *ast.ForAllSection {
	return &ast.ForAllSection{
		Targets: p.oneOrMoreTargets(section),
	}
}

func (p *parser) toThenSection(section phase4.Section) *ast.ThenSection {
	return &ast.ThenSection{
		Clauses: p.oneOrMoreClauses(section),
	}
}

/////////////////////////////// if ///////////////////////////////////////

func (p *parser) toIfGroup(group phase4.Group) (ast.IfGroup, bool) {
	if !startsWithSections(group, ast.LowerIfName) {
		return ast.IfGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.IfSections...)
	if !ok {
		return ast.IfGroup{}, false
	}
	return ast.IfGroup{
		If:   *p.toIfSection(sections[ast.LowerIfName]),
		Then: *p.toThenSection(sections[ast.LowerThenName]),
	}, true
}

func (p *parser) toIfSection(section phase4.Section) *ast.IfSection {
	return &ast.IfSection{
		Clauses: p.oneOrMoreClauses(section),
	}
}

/////////////////////////////// iff ///////////////////////////////////////

func (p *parser) toIffGroup(group phase4.Group) (ast.IffGroup, bool) {
	if !startsWithSections(group, ast.LowerIffName) {
		return ast.IffGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.IffSections...)
	if !ok {
		return ast.IffGroup{}, false
	}
	return ast.IffGroup{
		Iff:  *p.toIffSection(sections[ast.LowerIffName]),
		Then: *p.toThenSection(sections[ast.LowerThenName]),
	}, true
}

func (p *parser) toIffSection(section phase4.Section) *ast.IffSection {
	return &ast.IffSection{
		Clauses: p.oneOrMoreClauses(section),
	}
}

//////////////////////////////// when ////////////////////////////////////

func (p *parser) toWhenGroup(group phase4.Group) (ast.WhenGroup, bool) {
	if !startsWithSections(group, ast.LowerWhenName) {
		return ast.WhenGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.WhenSections...)
	if !ok {
		return ast.WhenGroup{}, false
	}
	return ast.WhenGroup{
		When: *p.toWhenSection(sections[ast.LowerWhenName]),
		Then: *p.toThenSection(sections[ast.LowerThenName]),
	}, true
}

func (p *parser) toWhenSection(section phase4.Section) *ast.WhenSection {
	return &ast.WhenSection{When: p.oneOrMoreClauses(section)}
}

///////////////////////////// piecewise /////////////////////////////////

func (p *parser) toPiecewiseGroup(group phase4.Group) (ast.PiecewiseGroup, bool) {
	if !startsWithSections(group, ast.LowerPiecewiseName) {
		return ast.PiecewiseGroup{}, false
	}

	sections := group.Sections
	if len(sections) == 0 || sections[0].Name != ast.LowerPiecewiseName {
		return ast.PiecewiseGroup{}, false
	}
	piecewise := *p.toPiecewiseSection(sections[0])
	ifThens := make([]ast.IfThen, 0)
	i := 1
	for i < len(sections) {
		section1 := sections[i]
		if section1.Name == ast.LowerElseName {
			break
		}
		i++
		if section1.Name != ast.LowerIfName {
			p.tracker.Append(newError(fmt.Sprintf("Expected section '%s' but found '%s'", ast.LowerIfName, section1.Name), section1.MetaData.Start))
			return ast.PiecewiseGroup{}, false
		}
		if i >= len(sections) {
			p.tracker.Append(newError(fmt.Sprintf("Expected section '%s' to follow an '%s' section", ast.LowerThenName, ast.LowerIfName), section1.MetaData.Start))
			return ast.PiecewiseGroup{}, false
		}
		section2 := sections[i]
		i++
		if section2.Name != ast.LowerThenName {
			p.tracker.Append(newError(fmt.Sprintf("Expected section '%s' but found '%s'", ast.LowerThenName, section2.Name), section2.MetaData.Start))
			return ast.PiecewiseGroup{}, false
		}
		ifThens = append(ifThens, ast.IfThen{
			If:   *p.toIfSection(section1),
			Then: *p.toThenSection(section2),
		})
	}
	var elseSec *ast.ElseSection
	if i < len(sections) && sections[i].Name == ast.LowerElseName {
		elseSec = p.toElseSection(sections[i])
		i++
	}
	invalid := i < len(sections)
	for i < len(sections) {
		sec := sections[i]
		i++
		p.tracker.Append(newError(fmt.Sprintf("Unexpected section '%s'", sec.Name), sec.MetaData.Start))
	}
	if invalid {
		return ast.PiecewiseGroup{}, false
	}
	return ast.PiecewiseGroup{
		Piecewise: piecewise,
		IfThen:    ifThens,
		Else:      elseSec,
	}, true
}

func (p *parser) toPiecewiseSection(section phase4.Section) *ast.PiecewiseSection {
	p.verifyNoArgs(section)
	return &ast.PiecewiseSection{}
}

func (p *parser) toElseSection(section phase4.Section) *ast.ElseSection {
	return &ast.ElseSection{
		Items: p.oneOrMoreClauses(section),
	}
}

//////////////////////////////////// provides ///////////////////////////////

func (p *parser) toSymbolWrittenGroup(group phase4.Group) (ast.SymbolWrittenGroup, bool) {
	if !startsWithSections(group, ast.LowerSymbolName) {
		return ast.SymbolWrittenGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.SymbolSections...)
	if !ok {
		return ast.SymbolWrittenGroup{}, false
	}
	symbol := *p.toSymbolSection(sections[ast.LowerSymbolName])
	var written *ast.WrittenSection
	if sect, ok := sections[ast.LowerWrittenName]; ok {
		written = p.toWrittenSection(sect)
	}
	return ast.SymbolWrittenGroup{
		Symbol:  symbol,
		Written: written,
	}, true
}

func (p *parser) toSymbolSection(section phase4.Section) *ast.SymbolSection {
	return &ast.SymbolSection{
		Symbol: p.exactlyOneAlias(section),
	}
}

func (p *parser) toWrittenSection(section phase4.Section) *ast.WrittenSection {
	return &ast.WrittenSection{
		Written: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toConnectionGroup(group phase4.Group) (ast.ConnectionGroup, bool) {
	if !startsWithSections(group, ast.LowerConnectionName) {
		return ast.ConnectionGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.ConnectionSections...)
	if !ok {
		return ast.ConnectionGroup{}, false
	}

	connection := *p.toConnectionSection(sections[ast.LowerConnectionName])
	var using *ast.UsingSection
	if sect, ok := sections[ast.LowerUsingName]; ok {
		using = p.toUsingSection(sect)
	}
	means := *p.toMeansSection(sections[ast.LowerMeansName])
	var signifies *ast.SignifiesSection
	if sect, ok := sections[ast.LowerSignifiesName]; ok {
		signifies = p.toSignifiesSection(sect)
	}
	var viewable *ast.ConnectionViewableSection
	if sect, ok := sections[ast.LowerViewableName]; ok {
		viewable = p.toConnectionViewableSection(sect)
	}
	var through *ast.ConnectionThroughSection
	if sect, ok := sections[ast.LowerThroughName]; ok {
		through = p.toConnectionThroughSection(sect)
	}
	return ast.ConnectionGroup{
		Connection: connection,
		Using:      using,
		Means:      means,
		Signfies:   signifies,
		Viewable:   viewable,
		Through:    through,
	}, true
}

func (p *parser) toConnectionSection(section phase4.Section) *ast.ConnectionSection {
	p.verifyNoArgs(section)
	return &ast.ConnectionSection{}
}

func (p *parser) toSignifiesSection(section phase4.Section) *ast.SignifiesSection {
	return &ast.SignifiesSection{
		Signifies: p.exactlyOneSpec(section),
	}
}

func (p *parser) toConnectionViewableSection(section phase4.Section) *ast.ConnectionViewableSection {
	p.verifyNoArgs(section)
	return &ast.ConnectionViewableSection{}
}

func (p *parser) toConnectionThroughSection(section phase4.Section) *ast.ConnectionThroughSection {
	return &ast.ConnectionThroughSection{
		Through: p.exactlyOneFormulation(section),
	}
}

func (p *parser) toOperationsSection(section phase4.Section) *ast.OperationsSection {
	p.verifyNoArgs(section)
	return &ast.OperationsSection{}
}

func (p *parser) toOnSection(section phase4.Section) *ast.OnSection {
	return &ast.OnSection{
		On: p.oneOrMoreTargets(section),
	}
}

func (p *parser) toSpecifySection(section phase4.Section) *ast.SpecifySection {
	return &ast.SpecifySection{
		Specify: p.oneOrMoreClauses(section),
	}
}

////////////////////// codified documented items /////////////////////////////////

func (p *parser) toWrittenGroup(group phase4.Group) (ast.WrittenGroup, bool) {
	if !startsWithSections(group, ast.LowerWrittenName) {
		return ast.WrittenGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.WrittenSections...)
	if !ok {
		return ast.WrittenGroup{}, false
	}
	return ast.WrittenGroup{
		Written: *p.toWrittenSection(sections[ast.LowerWrittenName]),
	}, true
}

func (p *parser) toCalledGroup(group phase4.Group) (ast.CalledGroup, bool) {
	if !startsWithSections(group, ast.LowerCalledName) {
		return ast.CalledGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.CalledSections...)
	if !ok {
		return ast.CalledGroup{}, false
	}
	return ast.CalledGroup{
		Called: *p.toCalledSection(sections[ast.LowerCalledName]),
	}, true
}

func (p *parser) toCalledSection(section phase4.Section) *ast.CalledSection {
	return &ast.CalledSection{
		Called: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toWritingGroup(group phase4.Group) (ast.WritingGroup, bool) {
	if !startsWithSections(group, ast.LowerWritingName) {
		return ast.WritingGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.WritingSections...)
	if !ok {
		return ast.WritingGroup{}, false
	}
	writing := *p.toWritingSection(sections[ast.LowerWritingName])
	as := *p.toWritingAsSection(sections[ast.LowerAsName])
	return ast.WritingGroup{
		Writing: writing,
		As:      as,
	}, true
}

func (p *parser) toWritingSection(section phase4.Section) *ast.WritingSection {
	return &ast.WritingSection{
		Writing: p.exactlyOneTarget(section),
	}
}

func (p *parser) toWritingAsSection(section phase4.Section) *ast.WritingAsSection {
	return &ast.WritingAsSection{
		As: p.oneOrMoreTextItems(section),
	}
}

////////////////////////// documented ////////////////////////////////////

func (p *parser) toDocumentedSection(section phase4.Section) *ast.DocumentedSection {
	return &ast.DocumentedSection{
		Documented: p.oneOrMoreDocumentedTypes(section),
	}
}

func (p *parser) toDocumentedType(arg phase4.Argument) (ast.DocumentedType, bool) {
	switch group := arg.Arg.(type) {
	case phase4.Group:
		if grp, ok := p.toOverviewGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toMotivationGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toHistoryGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toExampleGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toRelatedGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toDiscovererGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toNoteGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toWrittenGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toWritingGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toCalledGroup(group); ok {
			return grp, true
		}
	}

	return nil, false
}

func (p *parser) toOverviewGroup(group phase4.Group) (ast.OverviewGroup, bool) {
	if !startsWithSections(group, ast.LowerOverviewName) {
		return ast.OverviewGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.OverviewSections...)
	if !ok {
		return ast.OverviewGroup{}, false
	}
	return ast.OverviewGroup{
		Overview: *p.toOverviewSection(sections[ast.LowerOverviewName]),
	}, true
}

func (p *parser) toOverviewSection(section phase4.Section) *ast.OverviewSection {
	return &ast.OverviewSection{
		Overview: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toMotivationGroup(group phase4.Group) (ast.MotivationGroup, bool) {
	if !startsWithSections(group, ast.LowerMotivationName) {
		return ast.MotivationGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.MotivationSections...)
	if !ok {
		return ast.MotivationGroup{}, false
	}
	return ast.MotivationGroup{
		Motivation: *p.toMotivationSection(sections[ast.LowerMotivationName]),
	}, true
}

func (p *parser) toMotivationSection(section phase4.Section) *ast.MotivationSection {
	return &ast.MotivationSection{
		Motivation: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toHistoryGroup(group phase4.Group) (ast.HistoryGroup, bool) {
	if !startsWithSections(group, ast.LowerHistoryName) {
		return ast.HistoryGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.HistorySections...)
	if !ok {
		return ast.HistoryGroup{}, false
	}
	return ast.HistoryGroup{
		History: *p.toHistorySection(sections[ast.LowerHistoryName]),
	}, true
}

func (p *parser) toHistorySection(section phase4.Section) *ast.HistorySection {
	return &ast.HistorySection{
		History: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toExampleGroup(group phase4.Group) (ast.ExampleGroup, bool) {
	if !startsWithSections(group, ast.LowerExampleName) {
		return ast.ExampleGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.ExampleSections...)
	if !ok {
		return ast.ExampleGroup{}, false
	}
	return ast.ExampleGroup{
		Examples: *p.toExampleSection(sections[ast.LowerExampleName]),
	}, true
}

func (p *parser) toExampleSection(section phase4.Section) *ast.ExampleSection {
	return &ast.ExampleSection{
		Examples: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toRelatedGroup(group phase4.Group) (ast.RelatedGroup, bool) {
	if !startsWithSections(group, ast.LowerRelatedName) {
		return ast.RelatedGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.RelatedSections...)
	if !ok {
		return ast.RelatedGroup{}, false
	}
	return ast.RelatedGroup{
		Related: *p.toRelatedSection(sections[ast.LowerRelatedName]),
	}, true
}

func (p *parser) toRelatedSection(section phase4.Section) *ast.RelatedSection {
	return &ast.RelatedSection{
		Related: p.oneOrMoreSignatureItems(section),
	}
}

func (p *parser) toDiscovererGroup(group phase4.Group) (ast.DiscovererGroup, bool) {
	if !startsWithSections(group, ast.LowerDiscovererName) {
		return ast.DiscovererGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.DiscovererSections...)
	if !ok {
		return ast.DiscovererGroup{}, false
	}
	return ast.DiscovererGroup{
		Discoverer: *p.toDiscovererSection(sections[ast.LowerDiscovererName]),
	}, true
}

func (p *parser) toDiscovererSection(section phase4.Section) *ast.DiscovererSection {
	return &ast.DiscovererSection{
		Discoverer: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toNoteGroup(group phase4.Group) (ast.NoteGroup, bool) {
	if !startsWithSections(group, ast.LowerNoteName) {
		return ast.NoteGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.NoteSections...)
	if !ok {
		return ast.NoteGroup{}, false
	}
	return ast.NoteGroup{
		Note: *p.toNoteSection(sections[ast.LowerNoteName]),
	}, true
}

func (p *parser) toNoteSection(section phase4.Section) *ast.NoteSection {
	return &ast.NoteSection{
		Note: p.oneOrMoreNoteTypes(section),
	}
}

////////////////////////// provides //////////////////////////////////////

func (p *parser) toProvidesSection(section phase4.Section) *ast.ProvidesSection {
	return &ast.ProvidesSection{
		Provides: p.oneOrMoreProvidesType(section),
	}
}

func (p *parser) oneOrMoreProvidesType(section phase4.Section) []ast.ProvidesType {
	return oneOrMore(p.toProvidesTypes(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) toProvidesTypes(args []phase4.Argument) []ast.ProvidesType {
	result := make([]ast.ProvidesType, 0)
	for _, arg := range args {
		if providesType, ok := p.toProvidesTypeFromArg(arg); ok {
			result = append(result, providesType)
		}
	}
	return result
}

func (p *parser) toProvidesTypeFromArg(arg phase4.Argument) (ast.ProvidesType, bool) {
	if _, ok := arg.Arg.(phase4.FormulationArgumentData); ok {
		return p.toAlias(arg), true
	}

	group, ok := arg.Arg.(phase4.Group)
	if !ok {
		return nil, false
	}
	return p.toProvidesTypeFromGroup(group)
}

func (p *parser) toProvidesTypeFromGroup(group phase4.Group) (ast.ProvidesType, bool) {
	if grp, ok := p.toSymbolWrittenGroup(group); ok {
		return grp, true
	} else if grp, ok := p.toConnectionGroup(group); ok {
		return grp, ok
	} else {
		p.tracker.Append(newError(fmt.Sprintf("Unrecognized argument for %s:\n"+
			"Expected one of:\n\n%s:\n\n%s:\n",
			ast.UpperProvidesName,
			ast.LowerOperationsName,
			ast.LowerMembersName), group.Start()))
		return nil, false
	}
}

////////////////////////// aliases ////////////////////////////////////////

func (p *parser) toAliasesSection(section phase4.Section) *ast.AliasesSection {
	return &ast.AliasesSection{
		Aliases: p.oneOrMoreAliases(section),
	}
}

func (p *parser) toSingleAliasesSection(section phase4.Section) *ast.SingleAliasesSection {
	return &ast.SingleAliasesSection{
		Aliases: p.exactlyOneAlias(section),
	}
}

////////////////////////// justified ////////////////////////////////////

func (p *parser) toJustifiedSection(section phase4.Section) *ast.JustifiedSection {
	return &ast.JustifiedSection{
		Justified: p.oneOrMoreJustifiedType(section),
	}
}

func (p *parser) oneOrMoreJustifiedType(section phase4.Section) []ast.JustifiedType {
	return oneOrMore(p.toJustifiedTypes(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) toJustifiedTypes(args []phase4.Argument) []ast.JustifiedType {
	result := make([]ast.JustifiedType, 0)
	for _, arg := range args {
		if justifiedType, ok := p.toJustifiedTypeFromArg(arg); ok {
			result = append(result, justifiedType)
		}
	}
	return result
}

func (p *parser) toJustifiedTypeFromArg(arg phase4.Argument) (ast.JustifiedType, bool) {
	group, ok := arg.Arg.(phase4.Group)
	if !ok {
		return nil, false
	}
	return p.toJustifiedTypeFromGroup(group)
}

func (p *parser) toJustifiedTypeFromGroup(group phase4.Group) (ast.JustifiedType, bool) {
	if grp, ok := p.toLabelGroup(group); ok {
		return grp, true
	} else if grp, ok := p.toByGroup(group); ok {
		return grp, true
	} else {
		return nil, false
	}
}

func (p *parser) toLabelSection(section phase4.Section) *ast.LabelSection {
	return &ast.LabelSection{
		Label: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toLabelGroup(group phase4.Group) (ast.LabelGroup, bool) {
	if !startsWithSections(group, ast.LowerLabelName) {
		return ast.LabelGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker,
		ast.LowerLabelName,
		ast.LowerByName)
	if !ok {
		return ast.LabelGroup{}, false
	}
	return ast.LabelGroup{
		Label: *p.toLabelSection(sections[ast.LowerLabelName]),
		By:    *p.toBySection(sections[ast.LowerByName]),
	}, true
}

func (p *parser) toByGroup(group phase4.Group) (ast.ByGroup, bool) {
	if !startsWithSections(group, ast.LowerByName) {
		return ast.ByGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerByName)
	if !ok {
		return ast.ByGroup{}, false
	}
	return ast.ByGroup{
		By: *p.toBySection(sections[ast.LowerByName]),
	}, true
}

func (p *parser) toBySection(section phase4.Section) *ast.BySection {
	return &ast.BySection{
		By: p.oneOrMoreTextItems(section),
	}
}

////////////////////////// references ///////////////////////////////////

func (p *parser) toReferencesSection(section phase4.Section) *ast.ReferencesSection {
	return &ast.ReferencesSection{
		References: p.oneOrMoreTextItems(section),
	}
}

///////////////////////// meta id ////////////////////////////////////////

func (p *parser) toMetaIdSection(section phase4.Section) *ast.MetaIdSection {
	return &ast.MetaIdSection{
		Id: p.exactlyOneTextItem(section),
	}
}

//////////////////////// describes //////////////////////////////////////

func (p *parser) toDescribesGroup(group phase4.Group) (ast.DescribesGroup, bool) {
	if !startsWithSections(group, ast.UpperDescribesName) {
		return ast.DescribesGroup{}, false
	}

	id := p.getId(group, true)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.DescribesSections...)
	if !ok || id == nil {
		return ast.DescribesGroup{}, false
	}
	describes := *p.toDescribesSection(sections[ast.UpperDescribesName])
	var with *ast.WithSection
	if sec, ok := sections[ast.LowerWithName]; ok {
		with = p.toWithSection(sec)
	}
	var using *ast.UsingSection
	if sec, ok := sections[ast.LowerUsingName]; ok {
		using = p.toUsingSection(sec)
	}
	var when *ast.WhenSection
	if sec, ok := sections[ast.LowerWhenName]; ok {
		when = p.toWhenSection(sec)
	}
	var suchThat *ast.SuchThatSection
	if sec, ok := sections[ast.LowerSuchThatName]; ok {
		suchThat = p.toSuchThatSection(sec)
	}
	var extends *ast.ExtendsSection
	if sec, ok := sections[ast.LowerExtendsName]; ok {
		extends = p.toExtendsSection(sec)
	}
	var satisfies *ast.SatisfiesSection
	if sec, ok := sections[ast.LowerSatisfiesName]; ok {
		satisfies = p.toSatisfiesSection(sec)
	}
	var provides *ast.ProvidesSection
	if sec, ok := sections[ast.UpperProvidesName]; ok {
		provides = p.toProvidesSection(sec)
	}
	var justified *ast.JustifiedSection
	if sec, ok := sections[ast.UpperJustifiedName]; ok {
		justified = p.toJustifiedSection(sec)
	}
	var documented *ast.DocumentedSection
	if sec, ok := sections[ast.UpperDocumentedName]; ok {
		documented = p.toDocumentedSection(sec)
	}
	var references *ast.ReferencesSection
	if sec, ok := sections[ast.UpperReferencesName]; ok {
		references = p.toReferencesSection(sec)
	}
	var aliases *ast.AliasesSection
	if sec, ok := sections[ast.UpperAliasesName]; ok {
		aliases = p.toAliasesSection(sec)
	}
	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}
	return ast.DescribesGroup{
		Id:         *id,
		Describes:  describes,
		With:       with,
		Using:      using,
		When:       when,
		SuchThat:   suchThat,
		Extends:    extends,
		Satisfies:  satisfies,
		Provides:   provides,
		Justified:  justified,
		Documented: documented,
		References: references,
		Aliases:    aliases,
		MetaId:     metaId,
	}, true
}

func (p *parser) toDescribesSection(section phase4.Section) *ast.DescribesSection {
	return &ast.DescribesSection{
		Describes: p.exactlyOneTarget(section),
	}
}

func (p *parser) toExtendsSection(section phase4.Section) *ast.ExtendsSection {
	return &ast.ExtendsSection{
		Extends: p.oneOrMoreClauses(section),
	}
}

func (p *parser) toSatisfiesSection(section phase4.Section) *ast.SatisfiesSection {
	return &ast.SatisfiesSection{
		Satisfies: p.oneOrMoreClauses(section),
	}
}

/////////////////////// defines ////////////////////////////////////////

func (p *parser) toDefinesGroup(group phase4.Group) (ast.DefinesGroup, bool) {
	if !startsWithSections(group, ast.UpperDefinesName) {
		return ast.DefinesGroup{}, false
	}

	id := p.getId(group, true)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.DefinesSections...)
	if !ok || id == nil {
		return ast.DefinesGroup{}, false
	}
	defines := *p.toDefinesSection(sections[ast.UpperDefinesName])
	var with *ast.WithSection
	if sec, ok := sections[ast.LowerWithName]; ok {
		with = p.toWithSection(sec)
	}
	var using *ast.UsingSection
	if sec, ok := sections[ast.LowerUsingName]; ok {
		using = p.toUsingSection(sec)
	}
	var when *ast.WhenSection
	if sec, ok := sections[ast.LowerWhenName]; ok {
		when = p.toWhenSection(sec)
	}
	var suchThat *ast.SuchThatSection
	if sec, ok := sections[ast.LowerSuchThatName]; ok {
		suchThat = p.toSuchThatSection(sec)
	}
	var means *ast.MeansSection
	if sec, ok := sections[ast.LowerMeansName]; ok {
		means = p.toMeansSection(sec)
	}
	var specifies *ast.SpecifiesSection
	if sec, ok := sections[ast.LowerSpecifiesName]; ok {
		specifies = p.toSpecifiesSection(sec)
	}
	var provides *ast.ProvidesSection
	if sec, ok := sections[ast.UpperProvidesName]; ok {
		provides = p.toProvidesSection(sec)
	}
	var justified *ast.JustifiedSection
	if sec, ok := sections[ast.UpperJustifiedName]; ok {
		justified = p.toJustifiedSection(sec)
	}
	var documented *ast.DocumentedSection
	if sec, ok := sections[ast.UpperDocumentedName]; ok {
		documented = p.toDocumentedSection(sec)
	}
	var references *ast.ReferencesSection
	if sec, ok := sections[ast.UpperReferencesName]; ok {
		references = p.toReferencesSection(sec)
	}
	var aliases *ast.AliasesSection
	if sec, ok := sections[ast.UpperAliasesName]; ok {
		aliases = p.toAliasesSection(sec)
	}
	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}
	return ast.DefinesGroup{
		Id:         *id,
		Defines:    defines,
		With:       with,
		Using:      using,
		When:       when,
		SuchThat:   suchThat,
		Means:      means,
		Specifies:  specifies,
		Provides:   provides,
		Justified:  justified,
		Documented: documented,
		References: references,
		Aliases:    aliases,
		MetaId:     metaId,
	}, true
}

func (p *parser) toDefinesSection(section phase4.Section) *ast.DefinesSection {
	return &ast.DefinesSection{
		Defines: p.exactlyOneTarget(section),
	}
}

func (p *parser) toWithSection(section phase4.Section) *ast.WithSection {
	return &ast.WithSection{
		With: p.oneOrMoreTargets(section),
	}
}

func (p *parser) toMeansSection(section phase4.Section) *ast.MeansSection {
	return &ast.MeansSection{
		Means: p.exactlyOneClause(section),
	}
}

func (p *parser) toSpecifiesSection(section phase4.Section) *ast.SpecifiesSection {
	return &ast.SpecifiesSection{
		Specifies: p.oneOrMoreClauses(section),
	}
}

/////////////////////// states //////////////////////////////////////////

func (p *parser) toStatesGroup(group phase4.Group) (ast.StatesGroup, bool) {
	if !startsWithSections(group, ast.UpperStatesName) {
		return ast.StatesGroup{}, false
	}

	id := p.getId(group, true)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.StatesSections...)
	if !ok || id == nil {
		return ast.StatesGroup{}, false
	}
	states := *p.toStatesSection(sections[ast.UpperStatesName])
	var with *ast.WithSection
	if sec, ok := sections[ast.LowerWithName]; ok {
		with = p.toWithSection(sec)
	}
	var using *ast.UsingSection
	if sec, ok := sections[ast.LowerUsingName]; ok {
		using = p.toUsingSection(sec)
	}
	var when *ast.WhenSection
	if sec, ok := sections[ast.LowerWhenName]; ok {
		when = p.toWhenSection(sec)
	}
	var suchThat *ast.SuchThatSection
	if sec, ok := sections[ast.LowerSuchThatName]; ok {
		suchThat = p.toSuchThatSection(sec)
	}
	that := *p.toThatSection(sections[ast.LowerThatName])
	var documented *ast.DocumentedSection
	if sec, ok := sections[ast.UpperDocumentedName]; ok {
		documented = p.toDocumentedSection(sec)
	}
	var justified *ast.JustifiedSection
	if sec, ok := sections[ast.UpperJustifiedName]; ok {
		justified = p.toJustifiedSection(sec)
	}
	var references *ast.ReferencesSection
	if sec, ok := sections[ast.UpperReferencesName]; ok {
		references = p.toReferencesSection(sec)
	}
	var aliases *ast.AliasesSection
	if sec, ok := sections[ast.UpperAliasesName]; ok {
		aliases = p.toAliasesSection(sec)
	}
	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}
	return ast.StatesGroup{
		Id:         *id,
		States:     states,
		With:       with,
		Using:      using,
		When:       when,
		SuchThat:   suchThat,
		That:       that,
		Documented: documented,
		Justified:  justified,
		References: references,
		Aliases:    aliases,
		MetaId:     metaId,
	}, true
}

func (p *parser) toStatesSection(section phase4.Section) *ast.StatesSection {
	p.verifyNoArgs(section)
	return &ast.StatesSection{}
}

func (p *parser) toThatSection(section phase4.Section) *ast.ThatSection {
	return &ast.ThatSection{
		That: p.oneOrMoreClauses(section),
	}
}

/////////////////////// proof ///////////////////////////////////////////

// TODO: finish this

/////////////////////// axiom ///////////////////////////////////////////

func (p *parser) toAxiomGroup(group phase4.Group) (ast.AxiomGroup, bool) {
	if !startsWithSections(group, ast.UpperAxiomName) {
		return ast.AxiomGroup{}, false
	}

	id := p.getId(group, false)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.AxiomSections...)
	if !ok {
		return ast.AxiomGroup{}, false
	}
	axiom := *p.toAxiomSection(sections[ast.UpperAxiomName])
	var given *ast.GivenSection
	if sec, ok := sections[ast.LowerGivenName]; ok {
		given = p.toGivenSection(sec)
	}
	var where *ast.WhereSection
	if sec, ok := sections[ast.LowerWhereName]; ok {
		where = p.toWhereSection(sec)
	}
	var ifSec *ast.IfSection
	if sec, ok := sections[ast.LowerIfName]; ok {
		ifSec = p.toIfSection(sec)
	}
	var iff *ast.IffSection
	if sec, ok := sections[ast.LowerIffName]; ok {
		iff = p.toIffSection(sec)
	}
	then := *p.toThenSection(sections[ast.LowerThenName])
	var documented *ast.DocumentedSection
	if sec, ok := sections[ast.UpperDocumentedName]; ok {
		documented = p.toDocumentedSection(sec)
	}
	var references *ast.ReferencesSection
	if sec, ok := sections[ast.UpperReferencesName]; ok {
		references = p.toReferencesSection(sec)
	}
	var aliases *ast.AliasesSection
	if sec, ok := sections[ast.UpperAliasesName]; ok {
		aliases = p.toAliasesSection(sec)
	}
	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}
	return ast.AxiomGroup{
		Id:         id,
		Axiom:      axiom,
		Given:      given,
		Where:      where,
		If:         ifSec,
		Iff:        iff,
		Then:       then,
		Documented: documented,
		References: references,
		Aliases:    aliases,
		MetaId:     metaId,
	}, true
}

func (p *parser) toAxiomSection(section phase4.Section) *ast.AxiomSection {
	return &ast.AxiomSection{
		Axiom: p.zeroOrMoreTextItems(section),
	}
}

func (p *parser) toGivenSection(section phase4.Section) *ast.GivenSection {
	return &ast.GivenSection{
		Given: p.oneOrMoreTargets(section),
	}
}

func (p *parser) toUsingSection(section phase4.Section) *ast.UsingSection {
	return &ast.UsingSection{
		Using: p.oneOrMoreTargets(section),
	}
}

/////////////////////// conjecture //////////////////////////////////////

func (p *parser) toConjectureGroup(group phase4.Group) (ast.ConjectureGroup, bool) {
	if !startsWithSections(group, ast.UpperConjectureName) {
		return ast.ConjectureGroup{}, false
	}

	id := p.getId(group, false)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.ConjectureSections...)
	if !ok {
		return ast.ConjectureGroup{}, false
	}
	conjecture := *p.toConjectureSection(sections[ast.UpperConjectureName])
	var given *ast.GivenSection
	if sec, ok := sections[ast.LowerGivenName]; ok {
		given = p.toGivenSection(sec)
	}
	var where *ast.WhereSection
	if sec, ok := sections[ast.LowerWhereName]; ok {
		where = p.toWhereSection(sec)
	}
	var ifSec *ast.IfSection
	if sec, ok := sections[ast.LowerIfName]; ok {
		ifSec = p.toIfSection(sec)
	}
	var iff *ast.IffSection
	if sec, ok := sections[ast.LowerIffName]; ok {
		iff = p.toIffSection(sec)
	}
	then := *p.toThenSection(sections[ast.LowerThenName])
	var documented *ast.DocumentedSection
	if sec, ok := sections[ast.UpperDocumentedName]; ok {
		documented = p.toDocumentedSection(sec)
	}
	var references *ast.ReferencesSection
	if sec, ok := sections[ast.UpperReferencesName]; ok {
		references = p.toReferencesSection(sec)
	}
	var aliases *ast.AliasesSection
	if sec, ok := sections[ast.UpperAliasesName]; ok {
		aliases = p.toAliasesSection(sec)
	}
	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}
	return ast.ConjectureGroup{
		Id:         id,
		Conjecture: conjecture,
		Given:      given,
		Where:      where,
		If:         ifSec,
		Iff:        iff,
		Then:       then,
		Documented: documented,
		References: references,
		Aliases:    aliases,
		MetaId:     metaId,
	}, true
}

func (p *parser) toConjectureSection(section phase4.Section) *ast.ConjectureSection {
	return &ast.ConjectureSection{
		Conjecture: p.zeroOrMoreTextItems(section),
	}
}

/////////////////////// theorem /////////////////////////////////////////

func (p *parser) toTheoremGroup(group phase4.Group) (ast.TheoremGroup, bool) {
	if !startsWithSections(group, ast.UpperTheoremName) {
		return ast.TheoremGroup{}, false
	}

	id := p.getId(group, false)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.TheoremSections...)
	if !ok {
		return ast.TheoremGroup{}, false
	}
	theorem := *p.toTheoremSection(sections[ast.UpperTheoremName])
	var given *ast.GivenSection
	if sec, ok := sections[ast.LowerGivenName]; ok {
		given = p.toGivenSection(sec)
	}
	var where *ast.WhereSection
	if sec, ok := sections[ast.LowerWhereName]; ok {
		where = p.toWhereSection(sec)
	}
	var ifSec *ast.IfSection
	if sec, ok := sections[ast.LowerIfName]; ok {
		ifSec = p.toIfSection(sec)
	}
	var iff *ast.IffSection
	if sec, ok := sections[ast.LowerIffName]; ok {
		iff = p.toIffSection(sec)
	}
	then := *p.toThenSection(sections[ast.LowerThenName])
	var proof *ast.ProofSection
	if sec, ok := sections[ast.UpperProofName]; ok {
		proof = p.toProofSection(sec)
	}
	var documented *ast.DocumentedSection
	if sec, ok := sections[ast.UpperDocumentedName]; ok {
		documented = p.toDocumentedSection(sec)
	}
	var references *ast.ReferencesSection
	if sec, ok := sections[ast.UpperReferencesName]; ok {
		references = p.toReferencesSection(sec)
	}
	var aliases *ast.AliasesSection
	if sec, ok := sections[ast.UpperAliasesName]; ok {
		aliases = p.toAliasesSection(sec)
	}
	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}
	return ast.TheoremGroup{
		Id:         id,
		Theorem:    theorem,
		Given:      given,
		Where:      where,
		If:         ifSec,
		Iff:        iff,
		Then:       then,
		Proof:      proof,
		Documented: documented,
		References: references,
		Aliases:    aliases,
		MetaId:     metaId,
	}, true
}

func (p *parser) toTheoremSection(section phase4.Section) *ast.TheoremSection {
	return &ast.TheoremSection{
		Theorem: p.zeroOrMoreTextItems(section),
	}
}

func (p *parser) toProofSection(section phase4.Section) *ast.ProofSection {
	return &ast.ProofSection{
		Proof: p.exactlyOneTextItem(section),
	}
}

///////////////////////////////// text blocks /////////////////////////////

func (p *parser) toTextBlockItem(block phase4.TextBlock) *ast.TextBlockItem {
	return &ast.TextBlockItem{
		Text: block.Text,
	}
}

///////////////////////////////// specify /////////////////////////////////

func (p *parser) toSpecifyGroup(group phase4.Group) (ast.SpecifyGroup, bool) {
	if !startsWithSections(group, ast.UpperSpecifyName) {
		return ast.SpecifyGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.SpecifySections...)
	if !ok {
		return ast.SpecifyGroup{}, false
	}

	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}

	return ast.SpecifyGroup{
		Specify: *p.toTopLevelSpecifySection(sections[ast.UpperSpecifyName]),
		MetaId:  metaId,
	}, true
}

func (p *parser) toTopLevelSpecifySection(section phase4.Section) *ast.TopLevelSpecifySection {
	return &ast.TopLevelSpecifySection{
		Specify: p.oneOrMoreSpecifyTypes(section),
	}
}

func (p *parser) toSpecifyType(arg phase4.Argument) (ast.SpecifyType, bool) {
	switch group := arg.Arg.(type) {
	case phase4.Group:
		if grp, ok := p.toZeroGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toPositiveIntGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toNegativeIntGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toPositiveFloatGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toNegativeFloatGroup(group); ok {
			return grp, true
		}
	}
	return nil, false
}

func (p *parser) toZeroGroup(group phase4.Group) (ast.ZeroGroup, bool) {
	if !startsWithSections(group, ast.LowerZeroName) {
		return ast.ZeroGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.ZeroSections...)
	if !ok {
		return ast.ZeroGroup{}, false
	}
	return ast.ZeroGroup{
		Zero:  *p.toZeroSection(sections[ast.LowerZeroName]),
		Means: *p.toMeansSection(sections[ast.LowerMeansName]),
	}, true
}

func (p *parser) toZeroSection(section phase4.Section) *ast.ZeroSection {
	p.verifyNoArgs(section)
	return &ast.ZeroSection{}
}

func (p *parser) toPositiveIntGroup(group phase4.Group) (ast.PositiveIntGroup, bool) {
	if !startsWithSections(group, ast.LowerPositiveIntName) {
		return ast.PositiveIntGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.PositiveIntSections...)
	if !ok {
		return ast.PositiveIntGroup{}, false
	}
	return ast.PositiveIntGroup{
		PositiveInt: *p.toPositiveIntSection(sections[ast.LowerPositiveIntName]),
		Means:       *p.toMeansSection(sections[ast.LowerMeansName]),
	}, true
}

func (p *parser) toPositiveIntSection(section phase4.Section) *ast.PositiveIntSection {
	return &ast.PositiveIntSection{
		PositiveInt: p.exactlyOneTarget(section),
	}
}

func (p *parser) toNegativeIntGroup(group phase4.Group) (ast.NegativeIntGroup, bool) {
	if !startsWithSections(group, ast.LowerNegativeIntName) {
		return ast.NegativeIntGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.NegativeIntSections...)
	if !ok {
		return ast.NegativeIntGroup{}, false
	}
	return ast.NegativeIntGroup{
		NegativeInt: *p.toNegativeIntSection(sections[ast.LowerNegativeIntName]),
		Means:       *p.toMeansSection(sections[ast.LowerMeansName]),
	}, true
}

func (p *parser) toNegativeIntSection(section phase4.Section) *ast.NegativeIntSection {
	return &ast.NegativeIntSection{
		NegativeInt: p.exactlyOneTarget(section),
	}
}

func (p *parser) toPositiveFloatGroup(group phase4.Group) (ast.PositiveFloatGroup, bool) {
	if !startsWithSections(group, ast.LowerPositiveFloatName) {
		return ast.PositiveFloatGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.PositiveFloatSections...)
	if !ok {
		return ast.PositiveFloatGroup{}, false
	}
	return ast.PositiveFloatGroup{
		PositiveFloat: *p.toPositiveFloatSection(sections[ast.LowerPositiveFloatName]),
		Means:         *p.toMeansSection(sections[ast.LowerMeansName]),
	}, true
}

func (p *parser) toPositiveFloatSection(section phase4.Section) *ast.PositiveFloatSection {
	return &ast.PositiveFloatSection{
		PositiveFloat: p.exactlyOneTarget(section),
	}
}

func (p *parser) toNegativeFloatGroup(group phase4.Group) (ast.NegativeFloatGroup, bool) {
	if !startsWithSections(group, ast.LowerNegativeFloatName) {
		return ast.NegativeFloatGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.NegativeFloatSections...)
	if !ok {
		return ast.NegativeFloatGroup{}, false
	}
	return ast.NegativeFloatGroup{
		NegativeFloat: *p.toNegativeFloatSection(sections[ast.LowerNegativeFloatName]),
		Means:         *p.toMeansSection(sections[ast.LowerMeansName]),
	}, true
}

func (p *parser) toNegativeFloatSection(section phase4.Section) *ast.NegativeFloatSection {
	return &ast.NegativeFloatSection{
		NegativeFloat: p.exactlyOneTarget(section),
	}
}

///////////////////////////////// topic ////////////////////////////////////

func (p *parser) toTopicGroup(group phase4.Group) (ast.TopicGroup, bool) {
	if !startsWithSections(group, ast.UpperTopicName) {
		return ast.TopicGroup{}, false
	}

	id := p.getId(group, true)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.TopicSections...)
	if !ok || id == nil {
		return ast.TopicGroup{}, false
	}
	topic := *p.toTopicSection(sections[ast.UpperTopicName])
	content := *p.toContentSection(sections[ast.LowerContentName])
	var references *ast.ReferencesSection
	if sec, ok := sections[ast.UpperJustifiedName]; ok {
		references = p.toReferencesSection(sec)
	}
	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}
	return ast.TopicGroup{
		Id:         *id,
		Topic:      topic,
		Content:    content,
		References: references,
		MetaId:     metaId,
	}, true
}

func (p *parser) toTopicSection(section phase4.Section) *ast.TopicSection {
	p.verifyNoArgs(section)
	return &ast.TopicSection{}
}

func (p *parser) toContentSection(section phase4.Section) *ast.ContentSection {
	return &ast.ContentSection{
		Content: p.exactlyOneTextItem(section),
	}
}

///////////////////////////////////////////////////////////////////////////

func (p *parser) toResourceGroup(group phase4.Group) (ast.ResourceGroup, bool) {
	if !startsWithSections(group, ast.UpperResourceName) {
		return ast.ResourceGroup{}, false
	}

	id := p.getStringId(group, true)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.ResourceSections...)
	if !ok || id == nil {
		return ast.ResourceGroup{}, false
	}

	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}

	return ast.ResourceGroup{
		Id:       *id,
		Resource: *p.toResourceSection(sections[ast.UpperResourceName]),
		MetaId:   metaId,
	}, true
}

func (p *parser) toResourceSection(section phase4.Section) *ast.ResourceSection {
	return &ast.ResourceSection{
		Items: p.oneOrMoreResourceTypes(section),
	}
}

func (p *parser) toResourceType(arg phase4.Argument) (ast.ResourceType, bool) {
	switch group := arg.Arg.(type) {
	case phase4.Group:
		if grp, ok := p.toTitleGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toAuthorGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toOffsetGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toUrlGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toHomepageGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toTypeGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toEditionGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toEditorGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toInstitutionGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toJournalGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toPublisherGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toVolumeGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toMonthGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toYearGroup(group); ok {
			return grp, ok
		} else if grp, ok := p.toDescriptionGroup(group); ok {
			return grp, ok
		}
	}
	return nil, false
}

func (p *parser) toPersonGroup(group phase4.Group) (ast.PersonGroup, bool) {
	if !startsWithSections(group, ast.UpperPersonName) {
		return ast.PersonGroup{}, false
	}

	id := p.getStringId(group, true)
	sections, ok := IdentifySections(group.Sections, p.tracker, ast.PersonSections...)
	if !ok || id == nil {
		return ast.PersonGroup{}, false
	}

	var metaId *ast.MetaIdSection
	if sec, ok := sections[ast.UpperIdQuestionName]; ok {
		metaId = p.toMetaIdSection(sec)
	}

	return ast.PersonGroup{
		Id:     *id,
		Person: *p.toPersonSection(sections[ast.UpperPersonName]),
		MetaId: metaId,
	}, true
}

func (p *parser) toPersonSection(section phase4.Section) *ast.PersonSection {
	return &ast.PersonSection{
		Items: p.oneOrMorePersonTypes(section),
	}
}

///////////////////////////////////////////////////////////////////////////

func (p *parser) toTitleGroup(group phase4.Group) (ast.TitleGroup, bool) {
	if !startsWithSections(group, ast.LowerTitleName) {
		return ast.TitleGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerTitleName)
	if !ok {
		return ast.TitleGroup{}, false
	}
	return ast.TitleGroup{
		Title: *p.toTitleSection(sections[ast.LowerTitleName]),
	}, true
}

func (p *parser) toTitleSection(section phase4.Section) *ast.TitleSection {
	return &ast.TitleSection{
		Title: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toAuthorGroup(group phase4.Group) (ast.AuthorGroup, bool) {
	if !startsWithSections(group, ast.LowerAuthorName) {
		return ast.AuthorGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerAuthorName)
	if !ok {
		return ast.AuthorGroup{}, false
	}
	return ast.AuthorGroup{
		Author: *p.toAuthorSection(sections[ast.LowerAuthorName]),
	}, true
}

func (p *parser) toAuthorSection(section phase4.Section) *ast.AuthorSection {
	return &ast.AuthorSection{
		Author: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toOffsetGroup(group phase4.Group) (ast.OffsetGroup, bool) {
	if !startsWithSections(group, ast.LowerOffsetName) {
		return ast.OffsetGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerOffsetName)
	if !ok {
		return ast.OffsetGroup{}, false
	}
	return ast.OffsetGroup{
		Offset: *p.toOffsetSection(sections[ast.LowerOffsetName]),
	}, true
}

func (p *parser) toOffsetSection(section phase4.Section) *ast.OffsetSection {
	return &ast.OffsetSection{
		Offset: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toUrlGroup(group phase4.Group) (ast.UrlGroup, bool) {
	if !startsWithSections(group, ast.LowerUrlName) {
		return ast.UrlGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerUrlName)
	if !ok {
		return ast.UrlGroup{}, false
	}
	return ast.UrlGroup{
		Url: *p.toUlrSection(sections[ast.LowerUrlName]),
	}, true
}

func (p *parser) toUlrSection(section phase4.Section) *ast.UrlSection {
	return &ast.UrlSection{
		Url: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toHomepageGroup(group phase4.Group) (ast.HomepageGroup, bool) {
	if !startsWithSections(group, ast.LowerHomepageName) {
		return ast.HomepageGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerHomepageName)
	if !ok {
		return ast.HomepageGroup{}, false
	}
	return ast.HomepageGroup{
		Homepage: *p.toHomepageSection(sections[ast.LowerHomepageName]),
	}, true
}

func (p *parser) toHomepageSection(section phase4.Section) *ast.HomepageSection {
	return &ast.HomepageSection{
		Homepage: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toTypeGroup(group phase4.Group) (ast.TypeGroup, bool) {
	if !startsWithSections(group, ast.LowerTypeName) {
		return ast.TypeGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerTypeName)
	if !ok {
		return ast.TypeGroup{}, false
	}
	return ast.TypeGroup{
		Type: *p.toTypeSection(sections[ast.LowerTypeName]),
	}, true
}

func (p *parser) toTypeSection(section phase4.Section) *ast.TypeSection {
	return &ast.TypeSection{
		Type: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toEditionGroup(group phase4.Group) (ast.EditionGroup, bool) {
	if !startsWithSections(group, ast.LowerEditionName) {
		return ast.EditionGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerEditionName)
	if !ok {
		return ast.EditionGroup{}, false
	}
	return ast.EditionGroup{
		Edition: *p.toEditionSection(sections[ast.LowerEditionName]),
	}, true
}

func (p *parser) toEditionSection(section phase4.Section) *ast.EditionSection {
	return &ast.EditionSection{
		Edition: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toEditorGroup(group phase4.Group) (ast.EditorGroup, bool) {
	if !startsWithSections(group, ast.LowerEditorName) {
		return ast.EditorGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerEditorName)
	if !ok {
		return ast.EditorGroup{}, false
	}
	return ast.EditorGroup{
		Editor: *p.toEditorSection(sections[ast.LowerEditorName]),
	}, true
}

func (p *parser) toEditorSection(section phase4.Section) *ast.EditorSection {
	return &ast.EditorSection{
		Editor: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toInstitutionGroup(group phase4.Group) (ast.InstitutionGroup, bool) {
	if !startsWithSections(group, ast.LowerInstitutionName) {
		return ast.InstitutionGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerInstitutionName)
	if !ok {
		return ast.InstitutionGroup{}, false
	}
	return ast.InstitutionGroup{
		Institution: *p.toInstitutionSection(sections[ast.LowerInstitutionName]),
	}, true
}

func (p *parser) toInstitutionSection(section phase4.Section) *ast.InstitutionSection {
	return &ast.InstitutionSection{
		Institution: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toJournalGroup(group phase4.Group) (ast.JournalGroup, bool) {
	if !startsWithSections(group, ast.LowerJournalName) {
		return ast.JournalGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerJournalName)
	if !ok {
		return ast.JournalGroup{}, false
	}
	return ast.JournalGroup{
		Journal: *p.toJournalSection(sections[ast.LowerJournalName]),
	}, true
}

func (p *parser) toJournalSection(section phase4.Section) *ast.JournalSection {
	return &ast.JournalSection{
		Journal: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toPublisherGroup(group phase4.Group) (ast.PublisherGroup, bool) {
	if !startsWithSections(group, ast.LowerPublisherName) {
		return ast.PublisherGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerPublisherName)
	if !ok {
		return ast.PublisherGroup{}, false
	}
	return ast.PublisherGroup{
		Publisher: *p.toPublisherSection(sections[ast.LowerPublisherName]),
	}, true
}

func (p *parser) toPublisherSection(section phase4.Section) *ast.PublisherSection {
	return &ast.PublisherSection{
		Publisher: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toVolumeGroup(group phase4.Group) (ast.VolumeGroup, bool) {
	if !startsWithSections(group, ast.LowerVolumeName) {
		return ast.VolumeGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerVolumeName)
	if !ok {
		return ast.VolumeGroup{}, false
	}
	return ast.VolumeGroup{
		Volume: *p.toVolumeSection(sections[ast.LowerVolumeName]),
	}, true
}

func (p *parser) toVolumeSection(section phase4.Section) *ast.VolumeSection {
	return &ast.VolumeSection{
		Volume: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toMonthGroup(group phase4.Group) (ast.MonthGroup, bool) {
	if !startsWithSections(group, ast.LowerMonthName) {
		return ast.MonthGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerMonthName)
	if !ok {
		return ast.MonthGroup{}, false
	}
	return ast.MonthGroup{
		Month: *p.toMonthSection(sections[ast.LowerMonthName]),
	}, true
}

func (p *parser) toMonthSection(section phase4.Section) *ast.MonthSection {
	return &ast.MonthSection{
		Month: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toYearGroup(group phase4.Group) (ast.YearGroup, bool) {
	if !startsWithSections(group, ast.LowerYearName) {
		return ast.YearGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerYearName)
	if !ok {
		return ast.YearGroup{}, false
	}
	return ast.YearGroup{
		Year: *p.toYearSection(sections[ast.LowerYearName]),
	}, true
}

func (p *parser) toYearSection(section phase4.Section) *ast.YearSection {
	return &ast.YearSection{
		Year: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toDescriptionGroup(group phase4.Group) (ast.DescriptionGroup, bool) {
	if !startsWithSections(group, ast.LowerDescriptionName) {
		return ast.DescriptionGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.LowerDescriptionName)
	if !ok {
		return ast.DescriptionGroup{}, false
	}
	return ast.DescriptionGroup{
		Description: *p.toDescriptionSection(sections[ast.LowerDescriptionName]),
	}, true
}

func (p *parser) toDescriptionSection(section phase4.Section) *ast.DescriptionSection {
	return &ast.DescriptionSection{
		Description: p.exactlyOneTextItem(section),
	}
}

//////////////////////////////// top level items //////////////////////////

func (p *parser) toTopLevelItemType(item phase4.TopLevelNodeType) (ast.TopLevelItemType, bool) {
	switch item := item.(type) {
	case phase4.TextBlock:
		return p.toTextBlockItem(item), true
	case phase4.Group:
		if grp, ok := p.toDefinesGroup(item); ok {
			return grp, true
		} else if grp, ok := p.toDescribesGroup(item); ok {
			return grp, true
		} else if grp, ok := p.toStatesGroup(item); ok {
			return grp, true
		} else if grp, ok := p.toAxiomGroup(item); ok {
			return grp, true
		} else if grp, ok := p.toConjectureGroup(item); ok {
			return grp, true
		} else if grp, ok := p.toTheoremGroup(item); ok {
			return grp, true
		} else if grp, ok := p.toSpecifyGroup(item); ok {
			return grp, true
		} else if grp, ok := p.toTopicGroup(item); ok {
			return grp, ok
		} else if grp, ok := p.toPersonGroup(item); ok {
			return grp, ok
		} else if grp, ok := p.toResourceGroup(item); ok {
			return grp, ok
		}
	}
	p.tracker.Append(newError("Invalid top level item", item.Start()))
	return nil, false
}

///////////////////////////// document ///////////////////////////////////

func (p *parser) toDocument(root phase4.Root) (ast.Document, bool) {
	countBefore := p.tracker.Length()
	items := make([]ast.TopLevelItemType, 0)
	for _, node := range root.Nodes {
		if item, ok := p.toTopLevelItemType(node); ok {
			items = append(items, item)
		}
	}
	return ast.Document{
		Items: items,
	}, p.tracker.Length() == countBefore
}

///////////////////////////// id ////////////////////////////////////////

func (p *parser) toIdItem(text string, position ast.Position) *ast.IdItem {
	if node, ok := formulation.ParseId(text, position, p.tracker); ok {
		return &ast.IdItem{
			RawText: text,
			Root:    node,
			Label:   nil,
		}
	} else {
		return nil
	}
}

func (p *parser) getId(group phase4.Group, required bool) *ast.IdItem {
	if required && group.Id == nil {
		p.tracker.Append(newError("Expected a [...] item", group.MetaData.Start))
		return nil
	} else if group.Id == nil {
		return nil
	}
	return p.toIdItem(*group.Id, group.MetaData.Start)
}

func (p *parser) getStringId(group phase4.Group, required bool) *string {
	if required && group.Id == nil {
		p.tracker.Append(newError("Expected a [...] item", group.MetaData.Start))
		return nil
	} else if group.Id == nil {
		return nil
	}
	return group.Id
}

////////////////////////// arguments ////////////////////////////////////

func (p *parser) toFormulation(arg phase4.Argument) ast.Formulation[ast.NodeType] {
	switch data := arg.Arg.(type) {
	case phase4.FormulationArgumentData:
		if node, ok := formulation.ParseExpression(data.Text, arg.MetaData.Start, p.tracker); ok {
			return ast.Formulation[ast.NodeType]{
				RawText: data.Text,
				Root:    node,
				Label:   nil,
			}
		}
	}

	p.tracker.Append(newError("Expected a formulation", arg.MetaData.Start))
	return ast.Formulation[ast.NodeType]{}
}

func (p *parser) toClause(arg phase4.Argument) ast.Clause {
	switch data := arg.Arg.(type) {
	case phase4.FormulationArgumentData:
		if node, ok := formulation.ParseExpression(data.Text, arg.MetaData.Start, p.tracker); ok {
			return ast.Formulation[ast.NodeType]{
				RawText: data.Text,
				Root:    node,
				Label:   nil,
			}
		} else {
			return ast.Formulation[ast.NodeType]{}
		}
	case phase4.Group:
		if grp, ok := p.toAllOfGroup(data); ok {
			return grp
		} else if grp, ok := p.toNotGroup(data); ok {
			return grp
		} else if grp, ok := p.toAnyOfGroup(data); ok {
			return grp
		} else if grp, ok := p.toOneOfGroup(data); ok {
			return grp
		} else if grp, ok := p.toForAllGroup(data); ok {
			return grp
		} else if grp, ok := p.toExistsGroup(data); ok {
			return grp
		} else if grp, ok := p.toExistsUniqueGroup(data); ok {
			return grp
		} else if grp, ok := p.toIfGroup(data); ok {
			return grp
		} else if grp, ok := p.toIffGroup(data); ok {
			return grp
		} else if grp, ok := p.toWhenGroup(data); ok {
			return grp
		} else if grp, ok := p.toPiecewiseGroup(data); ok {
			return grp
		} else if grp, ok := p.toGivenGroup(data); ok {
			return grp
		}
	}

	p.tracker.Append(newError(fmt.Sprintf("Expected a '...', `...`, %s:, %s:, %s:, %s:, or %s: item",
		ast.LowerExistsName, ast.LowerExistsUniqueName, ast.LowerForAllName, ast.LowerIfName, ast.LowerIffName),
		arg.MetaData.Start))
	return ast.Formulation[ast.NodeType]{}
}

func (p *parser) toSpec(arg phase4.Argument) ast.Spec {
	switch data := arg.Arg.(type) {
	case phase4.FormulationArgumentData:
		if node, ok := formulation.ParseExpression(data.Text, arg.MetaData.Start, p.tracker); ok {
			return ast.Spec{
				RawText: data.Text,
				Root:    node,
				Label:   nil,
			}
		} else {
			return ast.Spec{}
		}
	default:
		p.tracker.Append(newError("Expected a '... is ...' or a '... <op> ...' item", arg.MetaData.Start))
		return ast.Spec{}
	}
}

func (p *parser) toAlias(arg phase4.Argument) ast.Alias {
	switch data := arg.Arg.(type) {
	case phase4.FormulationArgumentData:
		if node, ok := formulation.ParseExpression(data.Text, arg.MetaData.Start, p.tracker); ok {
			return ast.Alias{
				RawText: data.Text,
				Root:    node,
				Label:   nil,
			}
		}
	}
	p.tracker.Append(newError("Expected a '... :=> ...' item", arg.MetaData.Start))
	return ast.Alias{}
}

func (p *parser) toTarget(arg phase4.Argument) ast.Target {
	switch data := arg.Arg.(type) {
	case phase4.ArgumentTextArgumentData:
		if node, ok := formulation.ParseForm(data.Text, arg.MetaData.Start, p.tracker); ok {
			return ast.Target{
				RawText: data.Text,
				Root:    node,
				Label:   nil,
			}
		} else {
			return ast.Target{}
		}
	default:
		p.tracker.Append(newError("Expected a name, function, set, tuple, or ':=' declaration", arg.MetaData.Start))
		return ast.Target{}
	}
}

func (p *parser) toTextItem(arg phase4.Argument) ast.TextItem {
	switch data := arg.Arg.(type) {
	case phase4.TextArgumentData:
		return ast.TextItem{
			RawText: data.Text,
		}
	default:
		p.tracker.Append(newError("Expected a \"...\" item", arg.MetaData.Start))
		return ast.TextItem{}
	}
}

func (p *parser) toNoteType(arg phase4.Argument) (ast.NoteType, bool) {
	switch group := arg.Arg.(type) {
	case phase4.Group:
		return p.toDescribingGroup(group)
	default:
		return p.toTextItem(arg), true
	}
}

func (p *parser) toDescribingGroup(group phase4.Group) (ast.DescribingGroup, bool) {
	if !startsWithSections(group, ast.LowerDescribingName) {
		return ast.DescribingGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.DescribingSections...)
	if !ok {
		return ast.DescribingGroup{}, false
	}

	return ast.DescribingGroup{
		Describing: *p.toDescribingSection(sections[ast.LowerDescribingName]),
		Content:    *p.toContentSection(sections[ast.LowerContentName]),
	}, true
}

func (p *parser) toDescribingSection(section phase4.Section) *ast.DescribingSection {
	return &ast.DescribingSection{
		Describing: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toPersonType(arg phase4.Argument) (ast.PersonType, bool) {
	switch group := arg.Arg.(type) {
	case phase4.Group:
		if grp, ok := p.toNameGroup(group); ok {
			return grp, true
		} else if grp, ok := p.toBiographyGroup(group); ok {
			return grp, true
		}
	}
	return nil, false
}

func (p *parser) toNameGroup(group phase4.Group) (ast.NameGroup, bool) {
	if !startsWithSections(group, ast.LowerNameName) {
		return ast.NameGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.NameSections...)
	if !ok {
		return ast.NameGroup{}, false
	}

	return ast.NameGroup{
		Name: *p.toNameSection(sections[ast.LowerNameName]),
	}, true
}

func (p *parser) toNameSection(section phase4.Section) *ast.NameSection {
	return &ast.NameSection{
		Name: p.oneOrMoreTextItems(section),
	}
}

func (p *parser) toBiographyGroup(group phase4.Group) (ast.BiographyGroup, bool) {
	if !startsWithSections(group, ast.LowerBiographyName) {
		return ast.BiographyGroup{}, false
	}

	sections, ok := IdentifySections(group.Sections, p.tracker, ast.BiographySections...)
	if !ok {
		return ast.BiographyGroup{}, false
	}

	return ast.BiographyGroup{
		Biography: *p.toBiographySection(sections[ast.LowerBiographyName]),
	}, true
}

func (p *parser) toBiographySection(section phase4.Section) *ast.BiographySection {
	return &ast.BiographySection{
		Biography: p.exactlyOneTextItem(section),
	}
}

func (p *parser) toSignatureItem(arg phase4.Argument) ast.Formulation[ast.Signature] {
	switch data := arg.Arg.(type) {
	case phase4.FormulationArgumentData:
		if node, ok := formulation.ParseSignature(data.Text, arg.MetaData.Start, p.tracker); ok {
			return ast.Formulation[ast.Signature]{
				RawText: data.Text,
				Root:    node,
				Label:   nil,
			}
		} else {
			return ast.Formulation[ast.Signature]{}
		}
	}

	p.tracker.Append(newError("Expected a signature", arg.MetaData.Start))
	return ast.Formulation[ast.Signature]{}
}

//////////////////////// argument lists /////////////////////////////////

func (p *parser) toFormulations(args []phase4.Argument) []ast.Formulation[ast.NodeType] {
	result := make([]ast.Formulation[ast.NodeType], 0)
	for _, arg := range args {
		result = append(result, p.toFormulation(arg))
	}
	return result
}

func (p *parser) toClauses(args []phase4.Argument) []ast.Clause {
	result := make([]ast.Clause, 0)
	for _, arg := range args {
		result = append(result, p.toClause(arg))
	}
	return result
}

func (p *parser) toSpecs(args []phase4.Argument) []ast.Spec {
	result := make([]ast.Spec, 0)
	for _, arg := range args {
		result = append(result, p.toSpec(arg))
	}
	return result
}

func (p *parser) toAliases(args []phase4.Argument) []ast.Alias {
	result := make([]ast.Alias, 0)
	for _, arg := range args {
		result = append(result, p.toAlias(arg))
	}
	return result
}

func (p *parser) toTargets(args []phase4.Argument) []ast.Target {
	result := make([]ast.Target, 0)
	for _, arg := range args {
		result = append(result, p.toTarget(arg))
	}
	return result
}

func (p *parser) toTextItems(args []phase4.Argument) []ast.TextItem {
	result := make([]ast.TextItem, 0)
	for _, arg := range args {
		result = append(result, p.toTextItem(arg))
	}
	return result
}

func (p *parser) toNoteTypes(args []phase4.Argument) []ast.NoteType {
	result := make([]ast.NoteType, 0)
	for _, arg := range args {
		if note, ok := p.toNoteType(arg); ok {
			result = append(result, note)
		}
	}
	return result
}

func (p *parser) toPersonTypes(args []phase4.Argument) []ast.PersonType {
	result := make([]ast.PersonType, 0)
	for _, arg := range args {
		if note, ok := p.toPersonType(arg); ok {
			result = append(result, note)
		}
	}
	return result
}

func (p *parser) toResourceTypes(args []phase4.Argument) []ast.ResourceType {
	result := make([]ast.ResourceType, 0)
	for _, arg := range args {
		if note, ok := p.toResourceType(arg); ok {
			result = append(result, note)
		}
	}
	return result
}

func (p *parser) toSignaturesItems(args []phase4.Argument) []ast.Formulation[ast.Signature] {
	result := make([]ast.Formulation[ast.Signature], 0)
	for _, arg := range args {
		result = append(result, p.toSignatureItem(arg))
	}
	return result
}

func (p *parser) toDocumentedTypes(args []phase4.Argument) []ast.DocumentedType {
	result := make([]ast.DocumentedType, 0)
	for _, arg := range args {
		if doc, ok := p.toDocumentedType(arg); ok {
			result = append(result, doc)
		} else {
			p.tracker.Append(newError(fmt.Sprintf(
				"Expected a %s:, %s:, %s:, %s:, %s:, %s:, %s:, %s:, %s:, %s:, or %s: item",
				ast.LowerDetailsName, ast.LowerOverviewName, ast.LowerMotivationName, ast.LowerHistoryName,
				ast.LowerExampleName, ast.LowerRelatedName, ast.LowerDiscovererName, ast.LowerNoteName,
				ast.LowerWrittenName, ast.LowerWritingName, ast.LowerCalledName), arg.MetaData.Start))
		}
	}
	return result
}

func (p *parser) toSpecifyTypes(args []phase4.Argument) []ast.SpecifyType {
	result := make([]ast.SpecifyType, 0)
	for _, arg := range args {
		if spec, ok := p.toSpecifyType(arg); ok {
			result = append(result, spec)
		} else {
			p.tracker.Append(newError(fmt.Sprintf(
				"Expected a %s:, %s:, %s:, %s:, or %s: item",
				ast.LowerZeroName,
				ast.LowerPositiveIntName,
				ast.LowerNegativeIntName,
				ast.LowerPositiveFloatName,
				ast.LowerNegativeFloatName), arg.MetaData.Start))
		}
	}
	return result
}

/////////////////////////////////////////////////////////////////////////

func (p *parser) verifyNoArgs(section phase4.Section) {
	if len(section.Args) > 0 {
		p.tracker.Append(newError("Expected no arguments", section.MetaData.Start))
	}
}

func (p *parser) oneOrMoreClauses(section phase4.Section) []ast.Clause {
	return oneOrMore(p.toClauses(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) exactlyOneClause(section phase4.Section) ast.Clause {
	var def ast.Clause = ast.Formulation[ast.NodeType]{}
	return exactlyOne(p.toClauses(section.Args), def, section.MetaData.Start, p.tracker)
}

func (p *parser) exactlyOneFormulation(section phase4.Section) ast.Formulation[ast.NodeType] {
	var def ast.Formulation[ast.NodeType] = ast.Formulation[ast.NodeType]{}
	return exactlyOne(p.toFormulations(section.Args), def, section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMoreSpecs(section phase4.Section) []ast.Spec {
	return oneOrMore(p.toSpecs(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMoreAliases(section phase4.Section) []ast.Alias {
	return oneOrMore(p.toAliases(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) exactlyOneAlias(section phase4.Section) ast.Alias {
	var def ast.Alias = ast.Alias{}
	return exactlyOne(p.toAliases(section.Args), def, section.MetaData.Start, p.tracker)
}

func (p *parser) exactlyOneSpec(section phase4.Section) ast.Spec {
	var def ast.Spec = ast.Spec{}
	return exactlyOne(p.toSpecs(section.Args), def, section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMoreTargets(section phase4.Section) []ast.Target {
	return oneOrMore(p.toTargets(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMoreTextItems(section phase4.Section) []ast.TextItem {
	return oneOrMore(p.toTextItems(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) zeroOrMoreTextItems(section phase4.Section) []ast.TextItem {
	return p.toTextItems(section.Args)
}

func (p *parser) oneOrMoreNoteTypes(section phase4.Section) []ast.NoteType {
	return oneOrMore(p.toNoteTypes(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMorePersonTypes(section phase4.Section) []ast.PersonType {
	return oneOrMore(p.toPersonTypes(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMoreResourceTypes(section phase4.Section) []ast.ResourceType {
	return oneOrMore(p.toResourceTypes(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) exactlyOneTarget(section phase4.Section) ast.Target {
	var def ast.Target = ast.Target{}
	return exactlyOne(p.toTargets(section.Args), def, section.MetaData.Start, p.tracker)
}

func (p *parser) exactlyOneSignatureItem(section phase4.Section) ast.Formulation[ast.Signature] {
	var def ast.Formulation[ast.Signature] = ast.Formulation[ast.Signature]{}
	return exactlyOne(p.toSignaturesItems(section.Args), def, section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMoreSignatureItems(section phase4.Section) []ast.Formulation[ast.Signature] {
	return oneOrMore(p.toSignaturesItems(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) exactlyOneTextItem(section phase4.Section) ast.TextItem {
	return exactlyOne(p.toTextItems(section.Args), ast.TextItem{}, section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMoreDocumentedTypes(section phase4.Section) []ast.DocumentedType {
	return oneOrMore(p.toDocumentedTypes(section.Args), section.MetaData.Start, p.tracker)
}

func (p *parser) oneOrMoreSpecifyTypes(section phase4.Section) []ast.SpecifyType {
	return oneOrMore(p.toSpecifyTypes(section.Args), section.MetaData.Start, p.tracker)
}

////////////////////////// support functions ////////////////////////////

func oneOrMore[T any](items []T, position ast.Position, tracker frontend.DiagnosticTracker) []T {
	if len(items) == 0 {
		tracker.Append(newError("Expected at least one item", position))
		return []T{}
	}
	return items
}

func exactlyOne[T any](items []T, defaultItem T, position ast.Position, tracker frontend.DiagnosticTracker) T {
	if len(items) != 1 {
		tracker.Append(newError("Expected at exactly one item", position))
	}
	if len(items) == 0 {
		return defaultItem
	}
	return items[0]
}

func startsWithSections(group phase4.Group, names ...string) bool {
	if len(names) > len(group.Sections) {
		return false
	}

	for i, name := range names {
		if group.Sections[i].Name != name {
			return false
		}
	}

	return true
}

func endsWithSection(group phase4.Group, name string) bool {
	sections := group.Sections
	if len(sections) == 0 {
		return false
	} else {
		return sections[len(sections)-1].Name == name
	}
}

func newError(message string, position ast.Position) frontend.Diagnostic {
	return frontend.Diagnostic{
		Type:     frontend.Error,
		Origin:   frontend.Phase5ParserOrigin,
		Message:  message,
		Position: position,
	}
}

func sectionNamesToString(names []string) string {
	result := ""
	for i, name := range names {
		result += name + ":"
		if i != len(names)-1 {
			result += "\n"
		}
	}
	return result
}
